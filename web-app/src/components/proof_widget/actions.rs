//! Ability to get info on valid actions for a given proof line
//!
//! This module allows getting the description and keyboard shortcuts for all
//! valid actions on a given line.

use super::may_remove_line;
use super::LineActionKind;
use super::ProofItemKind;
use super::P;

use aris::proofs::pj_to_pjs;
use aris::proofs::PJRef;
use aris::proofs::Proof;

/// Information associated with a line action
pub struct ActionInfo {
    /// Short description of action, displayed in action selector menu
    pub description: &'static str,

    /// The keyboard shortcut to trigger this action, if any.
    ///
    /// The <kbd>Ctrl</kbd> key is implied. For example, `None` means that this
    /// action has no keyboard shortcut, and `Some('r')` means that the shortcut
    /// is <kbd>Ctrl-R</kbd>.
    pub keyboard_shortcut: Option<char>,

    /// The kind of this line action, used in `ProofWidgetMsg::LineAction`
    pub line_action_kind: LineActionKind,
}

/// Get an iterator over the valid actions in a line. For example,
/// premise-relative actions are only valid when the current line is a premise.
///
/// ## Parameters:
///   * `proof` - the current proof object
///   * `line_ref` - reference to the current line
pub fn valid_actions(proof: &P, line_ref: PJRef<P>) -> impl Iterator<Item = &ActionInfo> {
    use frunk_core::coproduct::Coproduct::{Inl, Inr};

    // Can the current line be deleted?
    let can_delete_line = may_remove_line(proof, &line_ref);

    // Is the current line a premise?
    let is_premise = matches!(line_ref, Inl(_));

    // Is the current line a justification?
    let is_just = matches!(line_ref, Inr(Inl(_)));

    // Is the current line in a subproof?
    let in_subproof = proof.parent_of_line(&pj_to_pjs::<P>(line_ref)).is_some();

    ACTIONS.iter().filter(move |action_info| match action_info.line_action_kind {
        LineActionKind::Insert { relative_to, what, .. } => {
            let valid = match relative_to {
                ProofItemKind::Premise => is_premise,
                ProofItemKind::Just => is_just,
                ProofItemKind::Subproof => in_subproof,
            };

            if what == ProofItemKind::Premise {
                // Subproofs should only have one assumption
                valid && !in_subproof
            } else {
                valid
            }
        }
        LineActionKind::Delete { what } => match what {
            ProofItemKind::Premise => is_premise && can_delete_line,
            ProofItemKind::Just => is_just && can_delete_line,
            ProofItemKind::Subproof => in_subproof,
        },
        _ => false,
    })
}

/// Array of all actions
static ACTIONS: [ActionInfo; 15] = [
    // Delete actions
    ActionInfo { keyboard_shortcut: Some('d'), description: "Delete premise", line_action_kind: LineActionKind::Delete { what: ProofItemKind::Premise } },
    ActionInfo { keyboard_shortcut: Some('d'), description: "Delete step", line_action_kind: LineActionKind::Delete { what: ProofItemKind::Just } },
    ActionInfo { keyboard_shortcut: None, description: "Delete subproof", line_action_kind: LineActionKind::Delete { what: ProofItemKind::Subproof } },
    // Insert actions
    // Subproof-relative insert actions
    ActionInfo { keyboard_shortcut: None, description: "Insert step before this subproof", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Just, after: false, relative_to: ProofItemKind::Subproof } },
    ActionInfo { keyboard_shortcut: Some('e'), description: "Insert step after this subproof", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Just, after: true, relative_to: ProofItemKind::Subproof } },
    ActionInfo { keyboard_shortcut: None, description: "Insert subproof before this subproof", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Subproof, after: false, relative_to: ProofItemKind::Subproof } },
    ActionInfo { keyboard_shortcut: None, description: "Insert subproof after this subproof", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Subproof, after: true, relative_to: ProofItemKind::Subproof } },
    // Premise-relative insert actions
    ActionInfo { keyboard_shortcut: None, description: "Insert premise before this premise", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Premise, after: false, relative_to: ProofItemKind::Premise } },
    ActionInfo { keyboard_shortcut: Some('r'), description: "Insert premise after this premise", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Premise, after: true, relative_to: ProofItemKind::Premise } },
    ActionInfo { keyboard_shortcut: Some('a'), description: "Insert step after this premise", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Just, after: true, relative_to: ProofItemKind::Premise } },
    // Step-relative insert actions
    ActionInfo { keyboard_shortcut: Some('b'), description: "Insert step before this step", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Just, after: false, relative_to: ProofItemKind::Just } },
    ActionInfo { keyboard_shortcut: Some('a'), description: "Insert step after this step", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Just, after: true, relative_to: ProofItemKind::Just } },
    ActionInfo { keyboard_shortcut: None, description: "Insert subproof before this step", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Subproof, after: false, relative_to: ProofItemKind::Just } },
    ActionInfo { keyboard_shortcut: Some('p'), description: "Insert subproof after this step", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Subproof, after: true, relative_to: ProofItemKind::Just } },
    ActionInfo { keyboard_shortcut: Some('r'), description: "Insert premise before this step", line_action_kind: LineActionKind::Insert { what: ProofItemKind::Premise, after: false, relative_to: ProofItemKind::Just } },
];
