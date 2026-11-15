fn main() {
    println!("cargo:rerun-if-changed=src/mail_transport.udl");
    uniffi::generate_scaffolding("src/mail_transport.udl").expect("UniFFI-Scaffolding");
}
