//! Utilities for macro expansion in UI frontends

/// Table of ASCII characters, macros, and their corresponding logic symbols.
/// The format of each row is `(symbol, macros)`.
pub static TABLE: [(&str, &[&str]); 10] = [
    ("⊥", &[".con", "^"]),
    ("⊤", &[".taut"]),
    ("¬", &[".not", "~"]),
    ("∀", &["forall", "@"]),
    ("∃", &["exists", "?"]),
    ("∧", &[".and", "&", r#"/\"#]),
    ("∨", &[".or", "|", r#"\/"#]),
    ("↔", &[".bicon", "%", "<->"]),
    ("→", &[".impl", "$", "->"]),
    ("≡", &[".equiv", "==="]),
];

/// Convert ASCII characters and macros to logic symbols.
///
/// ```rust
/// assert_eq!(
///     aris::macros::expand(".con -> (.taut .bicon ~P)"),
///     "⊥ → (⊤ ↔ ¬P)"
/// );
/// assert_eq!(
///     aris::macros::expand(
///         r#".con ^ .taut .not ~ forall @ exists ? .and & /\ .or | \/ .bicon % <-> .impl $ -> .equiv ==="#
///     ),
///     "⊥ ⊥ ⊤ ¬ ¬ ∀ ∀ ∃ ∃ ∧ ∧ ∧ ∨ ∨ ∨ ↔ ↔ ↔ → → → ≡ ≡"
/// );
/// ```
pub fn expand(s: &str) -> String {
    TABLE.iter().fold(s.to_string(), |s, (symbol, macros)| {
        macros.iter().fold(s, |s, macro_| s.replace(macro_, symbol))
    })
}
