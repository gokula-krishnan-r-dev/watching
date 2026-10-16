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
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        linux_impl::show(reason);
    }
    #[cfg(not(any(windows, unix)))]
    {
        tracing::info!(reason, "overlay proof stub — real UI via Tauri in d6");
    }
}

/// Dismiss any active overlay proof window.
pub fn dismiss_overlay_proof() {
    #[cfg(windows)]
    {
        windows_impl::dismiss();
    }
    #[cfg(target_os = "macos")]
    {
        macos_impl::dismiss();
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        linux_impl::dismiss();
    }
}

#[cfg(all(unix, not(target_os = "macos")))]
mod linux_impl {
    use std::process::Command;

    pub fn show(reason: &str) {
        tracing::info!(reason, "linux overlay assertion (d11 beta)");
        if std::env::var("DISPLAY").is_ok() && !meritscreen_enforcement::is_wayland() {
            let _ = Command::new("wmctrl").args(["-a", "MeritScreen"]).status();
        }
    }

    pub fn dismiss() {
        tracing::info!("linux overlay dismissed");
    }
}

#[cfg(target_os = "macos")]
mod macos_impl {
    use objc2::rc::Retained;
    use objc2::{MainThreadMarker, MainThreadOnly};
    use objc2_app_kit::{
        NSApplication, NSApplicationActivationPolicy, NSApplicationPresentationOptions,
        NSBackingStoreType, NSColor, NSWindow, NSWindowStyleMask,
    };
    use objc2_foundation::{NSPoint, NSRect, NSSize, NSString};

    thread_local! {
        static SHOWN: std::cell::Cell<bool> = const { std::cell::Cell::new(false) };
        static ACTIVE_WINDOW: std::cell::RefCell<Option<Retained<NSWindow>>> = const { std::cell::RefCell::new(None) };
    }

    pub fn show(reason: &str) -> Result<(), String> {
        if SHOWN.with(|s| s.get()) {
            if let Some(mtm) = MainThreadMarker::new() {
                let app = NSApplication::sharedApplication(mtm);
                app.activate();
                ACTIVE_WINDOW.with(|w| {
                    if let Some(win) = w.borrow().as_ref() {
                        win.makeKeyAndOrderFront(None);
                    }
                });
            }
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

        ACTIVE_WINDOW.with(|w| *w.borrow_mut() = Some(window));
        tracing::info!(reason, "macos shielding-level overlay proof shown");
        Ok(())
    }

    pub fn dismiss() {
        if let Some(mtm) = MainThreadMarker::new() {
            let app = NSApplication::sharedApplication(mtm);
            app.setPresentationOptions(NSApplicationPresentationOptions::empty());
        }
        if let Some(win) = ACTIVE_WINDOW.with(|w| w.borrow_mut().take()) {
            win.orderOut(None);
            win.close();
        }
        SHOWN.with(|s| s.set(false));
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
        CreateWindowExW, DefWindowProcW, DestroyWindow, DispatchMessageW, GetSystemMetrics,
        LoadCursorW, PeekMessageW, PostQuitMessage, RegisterClassW, ShowWindow, TranslateMessage,
        CS_HREDRAW, CS_VREDRAW, IDC_ARROW, MSG, PM_REMOVE, SM_CXSCREEN, SM_CYSCREEN, SW_SHOW,
        WINDOW_STYLE, WM_DESTROY, WM_PAINT, WNDCLASSW, WS_EX_TOOLWINDOW, WS_EX_TOPMOST, WS_POPUP,
        WS_VISIBLE,
    };

    thread_local! {
        static SHOWN: std::cell::Cell<bool> = const { std::cell::Cell::new(false) };
        static ACTIVE_HWND: std::cell::Cell<Option<HWND>> = const { std::cell::Cell::new(None) };
    }

    pub fn show(reason: &str) -> windows::core::Result<()> {
        if SHOWN.with(|s| s.get()) {
            if let Some(hwnd) = ACTIVE_HWND.with(|h| h.get()) {
                unsafe {
                    use windows::Win32::UI::WindowsAndMessaging::{
                        SetWindowPos, HWND_TOPMOST, SWP_NOMOVE, SWP_NOSIZE, SWP_SHOWWINDOW,
                    };
                    let _ = SetWindowPos(
                        hwnd,
                        HWND_TOPMOST,
                        0,
                        0,
                        0,
                        0,
                        SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW,
                    );
                }
            }
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

            ACTIVE_HWND.with(|h| h.set(Some(hwnd)));

            let boxed = Box::leak(reason.to_string().into_boxed_str());
            windows::Win32::UI::WindowsAndMessaging::SetWindowLongPtrW(
                hwnd,
                windows::Win32::UI::WindowsAndMessaging::GWLP_USERDATA,
                boxed.as_ptr() as isize,
            );

            let _ = ShowWindow(hwnd, SW_SHOW);
            let _ = UpdateWindow(hwnd);

            let mut msg = MSG::default();
            while PeekMessageW(&mut msg, None, 0, 0, PM_REMOVE).as_bool() {
                let _ = TranslateMessage(&msg);
                DispatchMessageW(&msg);
            }
        }
        tracing::info!(reason, "topmost fullscreen overlay proof shown");
        Ok(())
    }

    pub fn dismiss() {
        if let Some(hwnd) = ACTIVE_HWND.with(|h| h.take()) {
            unsafe {
                let _ = DestroyWindow(hwnd);
            }
        }
        SHOWN.with(|s| s.set(false));
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
