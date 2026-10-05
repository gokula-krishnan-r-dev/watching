//! Topmost / shielding-level fullscreen overlay proof (Windows Win32, macOS AppKit).

/// Show (or no-op) a topmost fullscreen lock/quiz placeholder window.
pub fn show_overlay_proof(reason: &str) {
    #[cfg(windows)]
    {
        if let Err(e) = windows_impl::show(reason) {
            tracing::warn!(error = %e, "overlay proof failed");
        }
    }
    #[cfg(target_os = "macos")]
    {
        if let Err(e) = macos_impl::show(reason) {
            tracing::warn!(error = %e, "macos shielding overlay proof failed");
        }
    }
    #[cfg(all(not(windows), not(target_os = "macos")))]
    {
        tracing::info!(
            reason,
            "overlay proof stub (non Win/macOS) — real UI via Tauri in d6"
        );
    }
}

#[cfg(target_os = "macos")]
mod macos_impl {
    use objc2::{MainThreadMarker, MainThreadOnly};
    use objc2_app_kit::{
        NSApplication, NSApplicationActivationPolicy, NSApplicationPresentationOptions,
        NSBackingStoreType, NSColor, NSWindow, NSWindowStyleMask,
    };
    use objc2_foundation::{NSPoint, NSRect, NSSize, NSString};

    thread_local! {
        static SHOWN: std::cell::Cell<bool> = const { std::cell::Cell::new(false) };
    }

    pub fn show(reason: &str) -> Result<(), String> {
        if SHOWN.with(|s| s.get()) {
            return Ok(());
        }
        SHOWN.with(|s| s.set(true));

        let mtm = MainThreadMarker::new()
            .ok_or_else(|| "overlay proof requires main thread".to_string())?;
        let app = NSApplication::sharedApplication(mtm);
        app.setActivationPolicy(NSApplicationActivationPolicy::Accessory);

        // Shielding-level window above normal apps (quiz / fail-lock proof).
        // NSWindow.Level shieldingWindowLevel == 25 (CGShieldingWindowLevel).
        let frame = NSRect {
            origin: NSPoint { x: 0.0, y: 0.0 },
            size: NSSize {
                width: 1280.0,
                height: 800.0,
            },
        };
        let window = unsafe {
            NSWindow::initWithContentRect_styleMask_backing_defer(
                NSWindow::alloc(mtm),
                frame,
                NSWindowStyleMask::Borderless,
                NSBackingStoreType::Buffered,
                false,
            )
        };
        window.setTitle(&NSString::from_str("MeritScreen Lock"));
        window.setBackgroundColor(Some(&NSColor::blackColor()));
        window.setLevel(25); // NSWindow.Level.shielding
        window.setCollectionBehavior(
            objc2_app_kit::NSWindowCollectionBehavior::CanJoinAllSpaces
                | objc2_app_kit::NSWindowCollectionBehavior::FullScreenAuxiliary,
        );
        window.setOpaque(true);
        window.makeKeyAndOrderFront(None);
        window.setFrame_display(
            NSRect {
                origin: NSPoint { x: 0.0, y: 0.0 },
                size: NSSize {
                    width: window
                        .screen()
                        .map(|s| s.frame().size.width)
                        .unwrap_or(1280.0),
                    height: window
                        .screen()
                        .map(|s| s.frame().size.height)
                        .unwrap_or(800.0),
                },
            },
            true,
        );

        // Best-effort kiosk presentation (hide Dock / menu) while overlay is up.
        let opts = NSApplicationPresentationOptions::HideDock
            | NSApplicationPresentationOptions::HideMenuBar
            | NSApplicationPresentationOptions::DisableForceQuit
            | NSApplicationPresentationOptions::DisableProcessSwitching;
        app.setPresentationOptions(opts);
        app.activate();

        // Keep a strong ref for process lifetime (proof only).
        // Full modal run loop would block the agent — d6 uses Tauri overlay.
        std::mem::forget(window);
        tracing::info!(reason, "macos shielding-level overlay proof shown");
        let _ = app;
        Ok(())
    }
}

#[cfg(windows)]
mod windows_impl {
    use std::ffi::OsStr;
    use std::os::windows::ffi::OsStrExt;
    use std::ptr;
    use windows::core::PCWSTR;
    use windows::Win32::Foundation::{HWND, LPARAM, LRESULT, RECT, WPARAM};
    use windows::Win32::Graphics::Gdi::{
        BeginPaint, CreateSolidBrush, EndPaint, FillRect, GetStockObject, UpdateWindow,
        BLACK_BRUSH, HBRUSH, PAINTSTRUCT,
    };
    use windows::Win32::System::LibraryLoader::GetModuleHandleW;
    use windows::Win32::UI::WindowsAndMessaging::{
        CreateWindowExW, DefWindowProcW, DispatchMessageW, GetMessageW, GetSystemMetrics,
        LoadCursorW, PostQuitMessage, RegisterClassW, ShowWindow, TranslateMessage, CS_HREDRAW,
        CS_VREDRAW, IDC_ARROW, MSG, SM_CXSCREEN, SM_CYSCREEN, SW_SHOW, WINDOW_STYLE, WM_DESTROY,
        WM_PAINT, WNDCLASSW, WS_EX_TOOLWINDOW, WS_EX_TOPMOST, WS_POPUP, WS_VISIBLE,
    };

    thread_local! {
        static SHOWN: std::cell::Cell<bool> = const { std::cell::Cell::new(false) };
    }

    pub fn show(reason: &str) -> windows::core::Result<()> {
        if SHOWN.with(|s| s.get()) {
            return Ok(());
        }
        SHOWN.with(|s| s.set(true));

        let title: Vec<u16> = OsStr::new("MeritScreen Lock")
            .encode_wide()
            .chain(std::iter::once(0))
            .collect();
        let class: Vec<u16> = OsStr::new("MeritScreenOverlayProof")
            .encode_wide()
            .chain(std::iter::once(0))
            .collect();

        unsafe {
            let hinstance = GetModuleHandleW(None)?;
            let wc = WNDCLASSW {
                style: CS_HREDRAW | CS_VREDRAW,
                lpfnWndProc: Some(wnd_proc),
                hInstance: hinstance.into(),
                hCursor: LoadCursorW(None, IDC_ARROW)?,
                hbrBackground: HBRUSH(GetStockObject(BLACK_BRUSH).0),
                lpszClassName: PCWSTR(class.as_ptr()),
                ..Default::default()
            };
            RegisterClassW(&wc);

            let cx = GetSystemMetrics(SM_CXSCREEN);
            let cy = GetSystemMetrics(SM_CYSCREEN);
            let hwnd = CreateWindowExW(
                WS_EX_TOPMOST | WS_EX_TOOLWINDOW,
                PCWSTR(class.as_ptr()),
                PCWSTR(title.as_ptr()),
                WINDOW_STYLE(WS_POPUP.0 | WS_VISIBLE.0),
                0,
                0,
                cx,
                cy,
                None,
                None,
                Some(hinstance.into()),
                Some(ptr::null_mut()),
            )?;

            let boxed = Box::leak(reason.to_string().into_boxed_str());
            windows::Win32::UI::WindowsAndMessaging::SetWindowLongPtrW(
                hwnd,
                windows::Win32::UI::WindowsAndMessaging::GWLP_USERDATA,
                boxed.as_ptr() as isize,
            );

            let _ = ShowWindow(hwnd, SW_SHOW);
            let _ = UpdateWindow(hwnd);

            let mut msg = MSG::default();
            for _ in 0..20 {
                if GetMessageW(&mut msg, None, 0, 0).into() {
                    let _ = TranslateMessage(&msg);
                    DispatchMessageW(&msg);
                }
            }
        }
        tracing::info!(reason, "topmost fullscreen overlay proof shown");
        Ok(())
    }

    unsafe extern "system" fn wnd_proc(
        hwnd: HWND,
        msg: u32,
        wparam: WPARAM,
        lparam: LPARAM,
    ) -> LRESULT {
        match msg {
            WM_PAINT => {
                let mut ps = PAINTSTRUCT::default();
                let hdc = BeginPaint(hwnd, &mut ps);
                let mut rect = RECT::default();
                let _ = windows::Win32::UI::WindowsAndMessaging::GetClientRect(hwnd, &mut rect);
                let brush = CreateSolidBrush(windows::Win32::Foundation::COLORREF(0x001A2124));
                let _ = FillRect(hdc, &rect, brush);
                let _ = EndPaint(hwnd, &ps);
                LRESULT(0)
            }
            WM_DESTROY => {
                PostQuitMessage(0);
                LRESULT(0)
            }
            _ => DefWindowProcW(hwnd, msg, wparam, lparam),
        }
    }
}
