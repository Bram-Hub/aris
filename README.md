[![Checks](https://github.com/Bram-Hub/aris/workflows/Checks/badge.svg)](https://github.com/Bram-Hub/aris/actions?query=workflow%3AChecks)
[![Security audit](https://github.com/Bram-Hub/aris/workflows/Security%20audit/badge.svg)](https://github.com/Bram-Hub/aris/actions?query=workflow%3A%22Security+audit%22)
[![Deploy pages](https://github.com/Bram-Hub/aris/workflows/Deploy%20pages/badge.svg)](https://github.com/Bram-Hub/aris/actions?query=workflow%3A%22Deploy+pages%22)

# Aris

## Web-Client Build

1. Install `wasm-pack` using `cargo install wasm-pack`
2. Build using `wasm-pack build web-app --target web --out-dir static/pkg`
3. Browse to `web-app/static/index.html` or serve with `python3 -m http.server`

## Auto-Grader Build

1. Build using `cargo build --release --bin aris-auto-grader`
2. Use with `target/release/aris-auto-grader <instructor assignment> <student assignment>`

## License

This project is licensed under the GNU GPLv3 License.
