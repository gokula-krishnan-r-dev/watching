fn main() {
    // Compile-time (release packaging) Firebase config: set these in the
    // build environment / CI secrets, never commit values.
    println!("cargo:rerun-if-env-changed=MERITSCREEN_FIREBASE_API_KEY");
    println!("cargo:rerun-if-env-changed=MERITSCREEN_FIREBASE_PROJECT_ID");
}
