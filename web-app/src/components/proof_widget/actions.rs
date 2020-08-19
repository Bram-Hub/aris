//! Ability to get info on valid actions for a given proof line
//!
//! This module allows getting the description and keyboard shortcuts for all
//! valid actions on a given line.

use super::may_remove_line;
use super::LAKItem;
use super::LineActionKind;
use super::P;

use aris::proofs::pj_to_pjs;
use aris::proofs::PJRef;
use aris::proofs::Proof;

/// The different classes of proof line actions. These are used to filter the
/// actions that are valid for certain lines.
enum ActionClass {
    /// Action deletes a line
    DeleteLine,

    /// Action is relative to a subproof
    SubproofRelative,

    /// Action is relative to a premise
    PremiseRelative,

    /// Action is relative to a step
    StepRelative,
}

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

    /// The class of this line action, for determining if it's valid for a line
    class: ActionClass,
}

/// Get an iterator over the valid actions in a line. For example,
/// premise-relative actions are only valid when the current line is a premise.
///
/// ## Parameters:
///   * `proof` - the current proof object
///   * `line_ref` - reference to the current line
pub fn valid_actions(proof: &P, line_ref: PJRef<P>) -> impl Iterator<Item = &ActionInfo> {
    use frunk_core::coproduct::Coproduct::{Inl, Inr};

    let can_delete = may_remove_line(proof, &line_ref);
    let is_premise = matches!(line_ref, Inl(_));
    let is_step = matches!(line_ref, Inr(Inl(_)));
    let in_subproof = proof.parent_of_line(&pj_to_pjs::<P>(line_ref)).is_some();

    ACTIONS.iter().filter(move |action_info| {
        match action_info.class {
            ActionClass::DeleteLine => can_delete,
            // Only allow subproof operations on non-root subproofs
            ActionClass::SubproofRelative => in_subproof,
            // Subproofs should only have one assumption
            ActionClass::PremiseRelative => is_premise && !in_subproof,
            ActionClass::StepRelative => is_step,
        }
    })
}

/// Array of all actions
static ACTIONS: [ActionInfo; 12] = [
    // Delete line
    ActionInfo {
        keyboard_shortcut: Some('d'),
        description: "Delete line",
        line_action_kind: LineActionKind::Delete {
            what: LAKItem::Line,
        },
        class: ActionClass::DeleteLine,
    },
    // Subproof-relative actions
    ActionInfo {
        keyboard_shortcut: None,
        description: "Delete subproof",
        line_action_kind: LineActionKind::Delete {
            what: LAKItem::Subproof,
        },
        class: ActionClass::SubproofRelative,
    },
    ActionInfo {
        keyboard_shortcut: None,
        description: "Insert step before this subproof",
        line_action_kind: LineActionKind::Insert {
            what: LAKItem::Line,
            after: false,
            relative_to: LAKItem::Subproof,
        },
        class: ActionClass::SubproofRelative,
    },
    ActionInfo {
        keyboard_shortcut: Some('e'),
        description: "Insert step after this subproof",
        line_action_kind: LineActionKind::Insert {
            what: LAKItem::Line,
            after: true,
            relative_to: LAKItem::Subproof,
        },
        class: ActionClass::SubproofRelative,
    },
    ActionInfo {
        keyboard_shortcut: None,
        description: "Insert subproof before this subproof",
        line_action_kind: LineActionKind::Insert {
            what: LAKItem::Subproof,
            after: false,
            relative_to: LAKItem::Subproof,
        },
        class: ActionClass::SubproofRelative,
    },
    ActionInfo {
        keyboard_shortcut: None,
        description: "Insert subproof after this subproof",
        line_action_kind: LineActionKind::Insert {
            what: LAKItem::Subproof,
            after: true,
            relative_to: LAKItem::Subproof,
        },
        class: ActionClass::SubproofRelative,
    },
    // Premise-relative actions
    ActionInfo {
        keyboard_shortcut: None,
        description: "Insert premise before this premise",
        line_action_kind: LineActionKind::Insert {
            what: LAKItem::Line,
            after: false,
            relative_to: LAKItem::Line,
        },
        class: ActionClass::PremiseRelative,
    },
    ActionInfo {
        keyboard_shortcut: Some('r'),
        description: "Insert premise after this premise",
        line_action_kind: LineActionKind::Insert {
            what: LAKItem::Line,
            after: true,
            relative_to: LAKItem::Line,
        },
        class: ActionClass::PremiseRelative,
    },
    // Step-relative actions
    ActionInfo {
        keyboard_shortcut: Some('b'),
        description: "Insert step before this step",
        line_action_kind: LineActionKind::Insert {
            what: LAKItem::Line,
            after: false,
            relative_to: LAKItem::Line,
        },
        class: ActionClass::StepRelative,
    },
    ActionInfo {
        keyboard_shortcut: Some('a'),
        description: "Insert step after this step",
        line_action_kind: LineActionKind::Insert {
            what: LAKItem::Line,
            after: true,
            relative_to: LAKItem::Line,
        },
        class: ActionClass::StepRelative,
    },
    ActionInfo {
        keyboard_shortcut: None,
        description: "Insert subproof before this step",
        line_action_kind: LineActionKind::Insert {
            what: LAKItem::Subproof,
            after: false,
            relative_to: LAKItem::Line,
        },
        class: ActionClass::StepRelative,
    },
    ActionInfo {
        keyboard_shortcut: Some('p'),
        description: "Insert subproof after this step",
        line_action_kind: LineActionKind::Insert {
            what: LAKItem::Subproof,
            after: true,
            relative_to: LAKItem::Line,
        },
        class: ActionClass::StepRelative,
    },
];
