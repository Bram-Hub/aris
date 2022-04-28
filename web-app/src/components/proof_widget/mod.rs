mod actions;

use crate::box_chars;
use crate::components::expr_entry::ExprEntry;
use crate::proof_ui_data::ProofUiData;
use crate::util::calculate_lineinfo;
use crate::util::P;

use aris::expr::Expr;
use aris::proofs::pj_to_pjs;
use aris::proofs::JsRef;
use aris::proofs::Justification;
use aris::proofs::PjRef;
use aris::proofs::PjsRef;
use aris::proofs::Proof;
use aris::rules::Rule;
use aris::rules::RuleClassification;
use aris::rules::RuleM;
use aris::rules::RuleT;

use std::collections::BTreeSet;
use std::fmt;
use std::mem;

use frunk_core::coproduct::Coproduct;
use frunk_core::Coprod;
use strum::IntoEnumIterator;
use yew::prelude::*;
use yew::utils::document;

use web_sys::HtmlElement;

use wasm_bindgen::JsCast;

use js_sys::Math::random;

/// Data stored for the currently selected line
struct SelectedLine {
    /// Reference to line in proof
    line_ref: PjRef<P>,

    /// Handle for listening for keyboard shortcuts
    #[allow(dead_code)]
    key_listener: yew::services::keyboard::KeyListenerHandle,
}

/// Component for editing proofs
pub struct ProofWidget {
    link: ComponentLink<Self>,

    /// The proof being edited with this widget
    prf: P,

    /// UI-specific data associated with the proof, such as intermediate text in
    /// lines that might have parse errors
    pud: ProofUiData<P>,

    /// The currently selected line, highlighted in the UI
    selected_line: Option<SelectedLine>,

    /// Error message, for if there was an error parsing the proof XML. If this
    /// exists, it is displayed instead of the proof.
    open_error: Option<String>,

    preblob: String,

    props: ProofWidgetProps,

    id: String,
}

/// A kind of proof structure item
#[derive(Debug, Copy, Clone, PartialEq, Eq)]
pub enum ProofItemKind {
    /// A premise
    Premise,
    /// A justification
    Just,
    /// A subproof
    Subproof,
}

#[derive(Debug, Clone)]
pub enum LineActionKind {
    Insert { what: ProofItemKind, after: bool, relative_to: ProofItemKind },
    Delete { what: ProofItemKind },
    SetRule { rule: Rule },
    Select,
    ToggleDependency { dep: Coprod![PjRef<P>, <P as Proof>::SubproofReference] },
}

/// Message for `ProofWidget`
pub enum ProofWidgetMsg {
    /// Do nothing
    Nop,
    LineChanged(PjRef<P>, String),
    LineAction(LineActionKind, PjRef<P>),
    CallOnProof(Box<dyn FnOnce(&P)>),
    /// Process keypress, handling any keyboard shortcuts
    Keypress(web_sys::KeyboardEvent),
}

impl fmt::Debug for ProofWidgetMsg {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        use self::ProofWidgetMsg::*;
        match self {
            Nop => f.debug_struct("Nop").finish(),
            LineChanged(r, s) => f.debug_tuple("LineChanged").field(&r).field(&s).finish(),
            LineAction(lak, r) => f.debug_tuple("LineAction").field(&lak).field(&r).finish(),
            CallOnProof(_) => f.debug_struct("CallOnProof").finish(),
            Keypress(key_event) => f.debug_tuple("Keypress").field(&key_event).finish(),
        }
    }
}

#[derive(Clone, Properties)]
pub struct ProofWidgetProps {
    pub verbose: bool,
    pub data: Option<Vec<u8>>,
    pub oncreate: Callback<ComponentLink<ProofWidget>>,
}

impl ProofWidget {
    fn render_line_num_dep_checkbox(&self, line: Option<usize>, proofref: Coprod!(PjRef<P>, <P as Proof>::SubproofReference)) -> Html {
        let line = match line {
            Some(line) => line.to_string(),
            None => "".to_string(),
        };
        if let Some(selected_line) = &self.selected_line {
            use Coproduct::{Inl, Inr};
            if let Inr(Inl(_)) = selected_line.line_ref {
                let line_ref = selected_line.line_ref;
                let toggle_dep = self.link.callback(move |_| ProofWidgetMsg::LineAction(LineActionKind::ToggleDependency { dep: proofref }, line_ref));
                if self.prf.can_reference_dep(&line_ref, &proofref) {
                    return html! {
                        <button
                            type="button"
                            class="btn btn-secondary"
                            onclick=toggle_dep>

                            { line }
                        </button>
                    };
                }
            }
        }
        html! {
            <button
                type="button"
                class="btn"
                disabled=true>

                { line }
            </button>
        }
    }
    /// Create a drop-down menu allowing the user to select the rule used in a
    /// justification line. This uses the [Bootstrap-submenu][lib] library.
    ///
    /// ## Parameters:
    ///   + `jref` - reference to the justification line containing this menu
    ///   + `cur_rule_name` - name of the current selected rule
    ///
    /// [lib]: https://github.com/vsn4ik/bootstrap-submenu
    fn render_rules_menu(&self, jref: <P as Proof>::JustificationReference, cur_rule_name: &str) -> Html {
        // Create menu items for rule classes
        let menu = RuleClassification::iter()
            .map(|rule_class| {
                // Create menu items for rules in class
                let rules = rule_class
                    .rules()
                    .map(|rule| {
                        let pjref = Coproduct::inject(jref);
                        // Create menu item for rule
                        html! {
                            <button class="dropdown-item" type="button" onclick=self.link.callback(move |_| ProofWidgetMsg::LineAction(LineActionKind::SetRule { rule }, pjref))>
                                { rule.get_name() }
                            </button>
                        }
                    })
                    .collect::<Vec<yew::virtual_dom::VNode>>();
                let rules = yew::virtual_dom::VList::new_with_children(rules, None);
                // Create sub-menu for rule class
                html! {
                    <div class="dropdown dropright dropdown-submenu">
                        <button class="dropdown-item dropdown-toggle" type="button" data-toggle="dropdown"> { rule_class } </button>
                        <div class="dropdown-menu"> { rules } </div>
                    </div>
                }
            })
            .collect::<Vec<yew::virtual_dom::VNode>>();
        let menu = yew::virtual_dom::VList::new_with_children(menu, None);

        // Create top-level menu button
        html! {
            <div class="dropright">
                <button class="btn btn-primary dropdown-toggle" type="button" data-toggle="dropdown" data-submenu="">
                    { cur_rule_name }
                </button>
                <div class="dropdown-menu">
                    { menu }
                </div>
                <script>
                    { "$('[data-submenu]').submenupicker()" }
                </script>
            </div>
        }
    }
    fn render_justification_widget(&self, jref: <P as Proof>::JustificationReference) -> Html {
        let just = self.prf.lookup_justification_or_die(&jref).expect("proofref should exist in self.prf");

        // Iterator over line dependency badges, for rendering list of
        // dependencies
        let dep_badges = just.2.iter().map(|dep| {
            let (dep_line, _) = self.pud.ref_to_line_depth[dep];
            html! {
                <span class="badge badge-dark m-1"> { dep_line } </span>
            }
        });

        // Iterator over subproof dependency badges, for rendering list of
        // dependencies
        let sdep_badges = just.3.iter().filter_map(|sdep| self.prf.lookup_subproof(sdep)).map(|sub| {
            let (mut lo, mut hi) = (usize::max_value(), usize::min_value());
            for line in sub.premises().into_iter().map(Coproduct::inject).chain(sub.direct_lines().into_iter().map(Coproduct::inject)) {
                if let Some((i, _)) = self.pud.ref_to_line_depth.get(&line) {
                    lo = std::cmp::min(lo, *i);
                    hi = std::cmp::max(hi, *i);
                }
            }
            let sdep_line = format!("{}-{}", lo, hi);
            html! {
                <span class="badge badge-secondary m-1"> { sdep_line } </span>
            }
        });

        // Node containing all dependency badges, for rendering list of
        // dependencies
        let all_dep_badges = dep_badges.chain(sdep_badges).collect::<Html>();

        let cur_rule_name = just.1.get_name();
        let rule_selector = self.render_rules_menu(jref, &cur_rule_name);
        html! {
            <>
                <td>
                    // Drop-down menu for selecting rules
                    { rule_selector }
                </td>
                <td>
                    // Dependency list
                    <span class="alert alert-secondary small-alert p-1">
                        { all_dep_badges }
                    </span>
                </td>
            </>
        }
    }
    fn render_line_feedback(&self, proofref: PjRef<P>, is_subproof: bool) -> Html {
        use aris::parser::parse;
        let raw_line = match self.pud.ref_to_input.get(&proofref).and_then(|x| if !x.is_empty() { Some(x) } else { None }) {
            None => {
                return html! { <span></span> };
            }
            Some(x) => x,
        };
        match parse(raw_line).map(|_| self.prf.verify_line(&proofref)) {
            None => {
                html! { <span class="alert alert-warning small-alert s1">{ "Parse error" }</span> }
            }
            Some(Ok(())) => match proofref {
                Coproduct::Inl(_) => html! {
                    <span class="alert alert-success small-alert s2">
                        { if is_subproof { "Assumption" } else { "Premise" } }
                    </span>
                },
                _ => {
                    html! { <span class="alert small-alert bg-success text-white s1">{ "Correct" }</span> }
                }
            },
            Some(Err(err)) => {
                html! {
                    <>
                        <button type="button" class="btn btn-danger s1" data-toggle="popover" data-content=err>
                            { "Error" }
                        </button>
                        <script>
                            { "$('[data-toggle=popover]').popover()" }
                        </script>
                    </>
                }
            }
        }
    }
    fn render_proof_line(&self, line: usize, depth: usize, proofref: PjRef<P>, edge_decoration: &str) -> Html {
        use Coproduct::{Inl, Inr};
        let line_num_dep_checkbox = self.render_line_num_dep_checkbox(Some(line), Coproduct::inject(proofref));
        let mut indentation = yew::virtual_dom::VList::new();
        for _ in 0..depth {
            //indentation.add_child(html! { <span style="background-color:black">{"-"}</span>});
            //indentation.add_child(html! { <span style="color:white">{"-"}</span>});
            indentation.add_child(html! { <span class="indent"> { box_chars::VERT } </span>});
        }
        indentation.add_child(html! { <span class="indent">{edge_decoration}</span>});
        let handle_input = self.link.callback(move |value: String| ProofWidgetMsg::LineChanged(proofref, value));
        let select_line = self.link.callback(move |()| ProofWidgetMsg::LineAction(LineActionKind::Select, proofref));

        // Menu for selecting a line action
        let action_selector = {
            // List of menu items
            let options = actions::valid_actions(&self.prf, proofref)
                .map(|action_info| {
                    let lak = action_info.line_action_kind.clone();

                    // Callback triggering line action
                    let onclick = self.link.callback(move |_| ProofWidgetMsg::LineAction(lak.clone(), proofref));

                    // Badge showing keyboard shortcut of action, if any
                    let keyboard_shortcut = match action_info.keyboard_shortcut {
                        Some(key) => {
                            html! {
                                <span>
                                    <kbd>
                                        <kbd> { "Ctrl" } </kbd>
                                        { '-' }
                                        <kbd> { key.to_uppercase() } </kbd>
                                    </kbd>
                                </span>
                            }
                        }
                        None => html!(),
                    };

                    // Item in line actions menu
                    html! {
                        <a class="dropdown-item" href="#" onclick=onclick>
                            { action_info.description }
                            { ' ' }
                            { keyboard_shortcut }
                        </a>
                    }
                })
                .collect::<Vec<Html>>();

            // Menu for selecting a line action
            html! {
                <div class="dropdown">
                    <button
                        type="button"
                        class="btn btn-secondary"
                        id="dropdownMenuButton"
                        data-toggle="dropdown"
                        aria-haspopup="true"
                        aria-expanded="false">

                        { "\u{22EE}" }
                    </button>
                    <div class="dropdown-menu" aria-labelledby="dropdownMenuButton">
                        { options }
                    </div>
                </div>
            }
        };
        let init_value = self.pud.ref_to_input.get(&proofref).cloned().unwrap_or_default();
        let in_subproof = depth > 0;
        let rule_feedback = self.render_line_feedback(proofref, in_subproof);
        let is_selected_line = self.selected_line.as_ref().map(|line| line.line_ref == proofref).unwrap_or(false);
        let is_dep_line = match self.selected_line {
            Some(SelectedLine { line_ref: Inr(Inl(selected_line)), .. }) => match self.prf.lookup_justification_or_die(&selected_line) {
                Ok(Justification(_, _, line_deps, _)) => line_deps.contains(&proofref),
                Err(_) => false,
            },
            _ => false,
        };
        let class = if is_selected_line {
            "proof-line table-info"
        } else if is_dep_line {
            "proof-line table-secondary"
        } else {
            "proof-line"
        };
        let feedback_and_just_widgets = match proofref {
            Inl(_) => {
                // Premise
                html! {
                    <>
                        <td></td>
                        <td> { rule_feedback } </td>
                        <td></td>
                    </>
                }
            }
            Inr(Inl(jref)) => {
                // Justification
                html! {
                    <>
                        <td> { rule_feedback } </td>
                        { self.render_justification_widget(jref) }
                    </>
                }
            }
            Inr(Inr(void)) => match void {},
        };
        let id_num = format!("{}{}{}", self.id, &"line-number-", &line.to_string());
        html! {
            <tr class=class>
                <td> { line_num_dep_checkbox } </td>
                <td>
                    { indentation }
                    <ExprEntry
                        oninput=handle_input
                        onfocus=select_line
                        focus=is_selected_line
                        init_value=init_value
                        id=id_num/>
                </td>
                { feedback_and_just_widgets }
                <td>{ action_selector }</td>
            </tr>
        }
    }

    fn render_proof(&self, prf: &<P as Proof>::Subproof, sref: Option<<P as Proof>::SubproofReference>, line: &mut usize, depth: &mut usize) -> Html {
        // output has a bool tag to prune subproof spacers with, because VNode's PartialEq doesn't do the right thing
        let mut output: Vec<(Html, bool)> = Vec::new();
        for prem in prf.premises().iter() {
            let edge_decoration = { box_chars::VERT }.to_string();
            output.push((self.render_proof_line(*line, *depth, Coproduct::inject(*prem), &edge_decoration), false));
            *line += 1;
        }
        let dep_checkbox = match sref {
            Some(sr) => self.render_line_num_dep_checkbox(None, Coproduct::inject(sr)),
            None => yew::virtual_dom::VNode::from(yew::virtual_dom::VList::new()),
        };
        let mut spacer = yew::virtual_dom::VList::new();
        spacer.add_child(html! { <td>{ dep_checkbox }</td> });
        //spacer.add_child(html! { <td style="background-color:black"></td> });
        let mut spacer_lines = String::new();
        for _ in 0..*depth {
            spacer_lines.push(box_chars::VERT);
        }
        spacer_lines += &format!("{}{}", box_chars::VERT_RIGHT, box_chars::HORIZ.to_string().repeat(4));
        spacer.add_child(html! { <td> <span class="indent"> {spacer_lines} </span> </td> });

        let spacer = html! { <tr> { spacer } </tr> };

        output.push((spacer, false));
        let prf_lines = prf.lines();
        for (i, lineref) in prf_lines.iter().enumerate() {
            use Coproduct::{Inl, Inr};
            let edge_decoration = if i == prf_lines.len() - 1 { box_chars::UP_RIGHT } else { box_chars::VERT }.to_string();
            match lineref {
                Inl(r) => {
                    output.push((self.render_proof_line(*line, *depth, Coproduct::inject(*r), &edge_decoration), false));
                    *line += 1;
                }
                Inr(Inl(sr)) => {
                    *depth += 1;
                    //output.push(row_spacer.clone());
                    output.push((self.render_proof(&prf.lookup_subproof(sr).unwrap(), Some(*sr), line, depth), false));
                    //output.push(row_spacer.clone());
                    *depth -= 1;
                }
                Inr(Inr(void)) => match *void {},
            }
        }
        // collapse 2 consecutive row spacers to just 1, formed by adjacent suproofs
        // also remove spacers at the end of an output (since that only occurs if a subproof is the last line of another subproof)
        // This can't be replaced with a range-based loop, since output.len() changes on removal
        {
            let mut i = 0;
            while i < output.len() {
                if output[i].1 && ((i == output.len() - 1) || output[i + 1].1) {
                    output.remove(i);
                }
                i += 1;
            }
        }
        let output: Vec<Html> = output.into_iter().map(|(x, _)| x).collect();
        let output = yew::virtual_dom::VList::new_with_children(output, None);
        if *depth == 0 {
            html! { <table>{ output }</table> }
        } else {
            yew::virtual_dom::VNode::from(output)
        }
    }

    /// Select the line referenced in `line_ref`. Also, set up a listener for
    /// line action keyboard shortcuts
    fn select_line(&mut self, line_ref: PjRef<P>) {
        let key_listener = yew::services::keyboard::KeyboardService::register_key_down(&yew::utils::window(), self.link.callback(ProofWidgetMsg::Keypress));

        self.selected_line = Some(SelectedLine { line_ref, key_listener });
    }

    /// Convert a keyboard shortcut into a `ProofWidgetMsg` that performs the
    /// action.
    ///
    /// NOTE: This overrides the behavior of built-in web browser shortcuts,
    /// such as <kbd>Ctrl-A</kbd> and <kbd>Ctrl-P</kbd>.
    fn process_key_shortcut(&self, key_event: web_sys::KeyboardEvent) -> ProofWidgetMsg {
        // Get the selected line, or do nothing if there is none
        let selected_line = match &self.selected_line {
            Some(selected_line) => selected_line.line_ref,
            None => return ProofWidgetMsg::Nop,
        };

        // All keyboard shortcuts have the control key held. Do nothing if the
        // control key isn't pressed.
        if !key_event.ctrl_key() {
            // Change focus on ArrowDown or ArrowUp
            if key_event.key() == "ArrowDown" || key_event.key() == "ArrowUp" {
                // Get our current id to find the others.
                let focused_elem_id = match document().active_element() {
                    Some(focused_elem_id) => focused_elem_id.id(),
                    None => return ProofWidgetMsg::Nop,
                };
                let up_down = match key_event.key().as_str() {
                    "ArrowDown" => 1,
                    "ArrowUp" => -1,
                    _ => return ProofWidgetMsg::Nop,
                };
                let signature = format!("{}{}", self.id, "line-number-");
                let length = signature.chars().count();
                // Verify that our selected element is the one we will work with.
                if focused_elem_id.chars().count() < length {
                    return ProofWidgetMsg::Nop;
                }
                let num = focused_elem_id[length..].parse::<i32>().unwrap() + up_down;
                //let new_id = "#line-number-".to_owned() + &num.to_string();
                let _focused_input = match document().get_element_by_id(&format!("{}{}", signature, &num.to_string())) {
                    Some(_focused_input) => _focused_input.unchecked_into::<HtmlElement>().focus(),
                    None => return ProofWidgetMsg::Nop,
                };
            }

            return ProofWidgetMsg::Nop;
        }

        // Some keyboard shortcuts (like Ctrl-A, Ctrl-P) conflict with typical
        // web browser keyboard shortcuts. This overrides their behavior.
        key_event.prevent_default();

        // Look up the triggered action
        let action = actions::valid_actions(&self.prf, selected_line).find(|action_info| action_info.keyboard_shortcut == key_event.key().chars().next());

        if let Some(action) = action {
            // Return action message
            let lak = action.line_action_kind.clone();
            ProofWidgetMsg::LineAction(lak, selected_line)
        } else {
            ProofWidgetMsg::Nop
        }
    }
}

/// Is the user allowed to remove the line at `line_ref`?
fn may_remove_line<P: Proof>(prf: &P, line_ref: &PjRef<P>) -> bool {
    use Coproduct::Inl;

    let is_premise = matches!(prf.lookup_pj(line_ref), Some(Inl(_)));

    let in_subproof = prf.parent_of_line(&pj_to_pjs::<P>(line_ref.clone())).is_some();

    if is_premise {
        if in_subproof {
            // Subproof premises can't be removed
            false
        } else {
            // Can't remove the last top-level premise
            prf.premises().len() > 1
        }
    } else {
        // Steps can always be removed
        true
    }
}

/// Render an alert for an error opening the proof
fn render_open_error(error: &str) -> Html {
    html! {
        <div class="alert alert-danger m-4" role="alert">
            <h4 class="alert-heading"> { "Error opening proof" } </h4>
            <hr />
            <p> { error } </p>
        </div>
    }
}

/// Create a new empty premise, the default premise when creating a new one in
/// the UI. The `ProofUiData` is supposed to be modified so this appears blank.
fn new_empty_premise() -> Expr {
    Expr::var("__js_ui_blank_premise")
}

/// Create a new empty step, the default step when creating a new one in the UI.
/// The `ProofUiData` is supposed to be modified so this appears blank.
fn new_empty_step() -> Justification<Expr, PjRef<P>, <P as Proof>::SubproofReference> {
    Justification(Expr::var("__js_ui_blank_step"), RuleM::EmptyRule, vec![], vec![])
}

/// Create a new empty proof, the default proof shown in the UI
fn new_empty_proof() -> (P, ProofUiData<P>) {
    let mut proof = P::new();
    proof.add_premise(new_empty_premise());

    let mut pud = ProofUiData::from_proof(&proof);
    for input in pud.ref_to_input.values_mut() {
        *input = "".to_string();
    }

    (proof, pud)
}

impl Component for ProofWidget {
    type Message = ProofWidgetMsg;
    type Properties = ProofWidgetProps;
    fn create(props: Self::Properties, link: ComponentLink<Self>) -> Self {
        props.oncreate.emit(link.clone());
        let (prf, pud, error) = match &props.data {
            Some(data) => {
                let result = aris::proofs::xml_interop::proof_from_xml::<P, _>(&data[..]);
                match result {
                    Ok((prf, _)) => {
                        let pud = ProofUiData::from_proof(&prf);
                        (prf, pud, None)
                    }
                    Err(err) => {
                        let (prf, pud) = new_empty_proof();
                        (prf, pud, Some(err))
                    }
                }
            }
            None => {
                let (prf, pud) = new_empty_proof();
                (prf, pud, None)
            }
        };

        let id: String = ((random() * 10000.0) as i32).to_string();

        let mut tmp = Self { link, prf, pud, selected_line: None, open_error: error, preblob: "".into(), props, id };
        tmp.update(ProofWidgetMsg::Nop);
        tmp
    }
    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        let mut ret = false;
        if self.props.verbose {
            self.preblob += &format!("{:?}\n", msg);
            ret = true;
        }
        use Coproduct::{Inl, Inr};
        match msg {
            ProofWidgetMsg::Nop => {}
            ProofWidgetMsg::LineChanged(r, input) => {
                self.pud.ref_to_input.insert(r, input.clone());
                if let Some(e) = aris::parser::parse(&input) {
                    match r {
                        Inl(pr) => {
                            self.prf.with_mut_premise(&pr, |x| *x = e);
                        }
                        Inr(Inl(jr)) => {
                            self.prf.with_mut_step(&jr, |x| x.0 = e);
                        }
                        Inr(Inr(void)) => match void {},
                    }
                }
                ret = true;
            }
            ProofWidgetMsg::LineAction(LineActionKind::Insert { what, after, relative_to }, orig_ref) => {
                let to_select;
                let orig_ref = pj_to_pjs::<P>(orig_ref);
                let parent = self.prf.parent_of_line(&orig_ref);
                let insertion_point: PjsRef<P> = match relative_to {
                    ProofItemKind::Premise | ProofItemKind::Just => orig_ref,
                    ProofItemKind::Subproof => match parent {
                        Some(parent) => Coproduct::inject(parent),
                        None => return ret,
                    },
                };
                match what {
                    ProofItemKind::Premise => match insertion_point {
                        Inl(pr) => {
                            // Insert premise relative to premise
                            to_select = Inl(self.prf.add_premise_relative(new_empty_premise(), &pr, after));
                        }
                        Inr(Inl(_)) | Inr(Inr(Inl(_))) => {
                            // Insert premise relative to line or subproof
                            to_select = Inl(self.prf.add_premise(new_empty_premise()));
                        }
                        Inr(Inr(Inr(void))) => match void {},
                    },
                    ProofItemKind::Just => match insertion_point {
                        Inl(_) => {
                            // Insert justification relative to premise

                            // Add justification to enclosing subproof of premise, if it exists
                            let just_ref = parent.and_then(|parent| self.prf.with_mut_subproof(&parent, |parent| parent.prepend_step(new_empty_step())));

                            // If the insertion point is not in a subproof, add justification to the top-level proof
                            match just_ref {
                                Some(just_ref) => to_select = Coproduct::inject(just_ref),
                                None => to_select = Coproduct::inject(self.prf.prepend_step(new_empty_step())),
                            }
                        }
                        Inr(Inl(jr)) => {
                            // Insert justification relative to justification
                            let jsr = Coproduct::inject(jr);
                            to_select = Inr(Inl(self.prf.add_step_relative(new_empty_step(), &jsr, after)));
                        }
                        Inr(Inr(Inl(sr))) => {
                            // Insert justification relative to subproof
                            let jsr = Coproduct::inject(sr);
                            to_select = Inr(Inl(self.prf.add_step_relative(new_empty_step(), &jsr, after)));
                        }
                        Inr(Inr(Inr(void))) => match void {},
                    },
                    ProofItemKind::Subproof => {
                        // Convert insertion point from `PjsRef` to `JsRef`,
                        // returning silently on failure
                        let insertion_point: JsRef<P> = match insertion_point.subset() {
                            Ok(insertion_point) => insertion_point,
                            // Insertion point is a premise, return silently
                            Err(_) => return ret,
                        };
                        let sr = self.prf.add_subproof_relative(&insertion_point, after);
                        to_select = self
                            .prf
                            .with_mut_subproof(&sr, |sub| {
                                let to_select = Inl(sub.add_premise(new_empty_premise()));
                                sub.prepend_step(new_empty_step());
                                to_select
                            })
                            .expect("Subproof doesn't exist after creating it");
                    }
                }
                self.select_line(to_select);
                self.preblob += &format!("{:?}\n", self.prf.premises());
                ret = true;
            }
            ProofWidgetMsg::LineAction(LineActionKind::Delete { what }, proofref) => {
                let parent = self.prf.parent_of_line(&pj_to_pjs::<P>(proofref));
                match what {
                    ProofItemKind::Premise | ProofItemKind::Just => {
                        fn remove_line_if_allowed<P: Proof, Q: Proof<PremiseReference = <P as Proof>::PremiseReference, JustificationReference = <P as Proof>::JustificationReference>>(prf: &mut Q, pud: &mut ProofUiData<P>, proofref: PjRef<Q>) {
                            if may_remove_line(prf, &proofref) {
                                pud.ref_to_line_depth.remove(&proofref);
                                pud.ref_to_input.remove(&proofref);
                                prf.remove_line(&proofref);
                            }
                        }
                        match parent {
                            Some(sr) => {
                                let pud = &mut self.pud;
                                self.prf.with_mut_subproof(&sr, |sub| {
                                    remove_line_if_allowed(sub, pud, proofref);
                                });
                            }
                            None => {
                                remove_line_if_allowed(&mut self.prf, &mut self.pud, proofref);
                            }
                        }
                    }
                    ProofItemKind::Subproof => {
                        // TODO: recursively clean out the ProofUiData entries for lines inside a subproof before deletion
                        // shouldn't delete the root subproof
                        if let Some(sr) = parent {
                            self.prf.remove_subproof(&sr);
                        }
                    }
                }
                // Deselect current line to prevent it from pointing to a
                // deleted line. The selected line could be deep inside a
                // deleted subproof, so it's easier to deselect conservatively
                // than to figure out if the selected line is deleted.
                self.selected_line = None;
                ret = true;
            }
            ProofWidgetMsg::LineAction(LineActionKind::SetRule { rule }, proofref) => {
                if let Inr(Inl(jr)) = &proofref {
                    self.prf.with_mut_step(jr, |j| j.1 = rule);
                }
                self.select_line(proofref);
                ret = true;
            }
            ProofWidgetMsg::LineAction(LineActionKind::Select, proofref) => {
                self.select_line(proofref);
                ret = true;
            }
            ProofWidgetMsg::LineAction(LineActionKind::ToggleDependency { dep }, proofref) => {
                if let Inr(Inl(jr)) = &proofref {
                    self.prf.with_mut_step(jr, |j| {
                        fn toggle_dep_or_sdep<T: Ord>(dep: T, deps: &mut Vec<T>) {
                            let mut dep_set: BTreeSet<T> = mem::take(deps).into_iter().collect();
                            if dep_set.contains(&dep) {
                                dep_set.remove(&dep);
                            } else {
                                dep_set.insert(dep);
                            }
                            deps.extend(dep_set);
                        }
                        match dep {
                            Inl(lr) => toggle_dep_or_sdep(lr, &mut j.2),
                            Inr(Inl(sr)) => toggle_dep_or_sdep(sr, &mut j.3),
                            Inr(Inr(void)) => match void {},
                        }
                    });
                }
                ret = true;
            }
            ProofWidgetMsg::CallOnProof(f) => {
                f(&self.prf);
            }
            ProofWidgetMsg::Keypress(key_event) => {
                let msg = self.process_key_shortcut(key_event);
                ret = self.update(msg);
            }
        }
        if ret {
            calculate_lineinfo::<P>(&mut self.pud.ref_to_line_depth, self.prf.top_level_proof(), &mut 1, &mut 0);
        }
        ret
    }
    fn change(&mut self, props: Self::Properties) -> ShouldRender {
        self.props = props;
        true
    }
    fn view(&self) -> Html {
        let widget = match &self.open_error {
            Some(err) => render_open_error(err),
            None => self.render_proof(self.prf.top_level_proof(), None, &mut 1, &mut 0),
        };
        html! {
            <div>
                { widget }
                <div style="display: none">
                    <hr />
                    <pre> { format!("{}\n{:#?}", self.prf, self.prf) } </pre>
                    <hr />
                    <pre> { self.preblob.clone() } </pre>
                </div>
            </div>
        }
    }
}
