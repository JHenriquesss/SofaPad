// Embute o ícone e as informações de versão no executável do Windows.
fn main() {
    println!("cargo:rerun-if-changed=assets/sofapad.ico");
    if std::env::var_os("CARGO_CFG_WINDOWS").is_none() {
        return;
    }
    let mut resource = winresource::WindowsResource::new();
    resource.set_icon_with_id("assets/sofapad.ico", "1");
    resource.set("ProductName", "SofaPad");
    resource.set("FileDescription", "SofaPad");
    resource.set("OriginalFilename", "SofaPad-agent.exe");
    resource.set("LegalCopyright", "Projeto SofaPad");
    resource.compile().expect("recursos do Windows");
}
