Bazel experiments
---

### Scala

- [x] Build
- [x] Test (via JUnit)
- [x] External dependencies (via rules_jvm_external)
- [ ] Docker image (cross-platform)
- [ ] IDE support

### Rust

- [x] Build
- [x] External dependencies (via crates)
- [ ] Test
- [x] Docker image (cross-platform)
- [x] IDE support

To generate IDE support for Rust run `bazel run @rules_rust//tools/rust_analyzer:gen_rust_project`

### TypeScript / React

- [ ] Build
- [ ] Test
- [ ] Docker image
- [ ] IDE support