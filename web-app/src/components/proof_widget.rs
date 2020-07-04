use crate::box_chars;
use crate::components::expr_entry::ExprEntry;
use crate::proof_ui_data::ProofUiData;
use crate::util::calculate_lineinfo;
use crate::util::P;

use aris::proofs::pj_to_pjs;
use aris::proofs::Justification;
use aris::proofs::Proof;
use aris::proofs::PJRef;
use aris::rules::Rule;
use aris::rules::RuleClassification;
use aris::rules::RuleM;
use aris::rules::RuleT;

use std::collections::BTreeSet;
use std::fmt;
use std::mem;

use frunk::Coprod;
use frunk::Coproduct;
use strum::IntoEnumIterator;
use yew::prelude::*;

/// Component for editing proofs
pub struct ProofWidget {
    link: ComponentLink<Self>,
    /// The proof being edited with this widget
    prf: P,
    /// UI-specific data associated with the proof, such as intermediate text in
    /// lines that might have parse errors
    pud: ProofUiData<P>,
    /// The currently selected line, highlighted in the UI
    selected_line: Option<PJRef<P>>,
    /// Error message, for if there was an error parsing the proof XML. If this
    /// exists, it is displayed instead of the proof.
    open_error: Option<String>,
    preblob: String,
    props: ProofWidgetProps,
}

#[derive(Debug)]
pub enum LAKItem {
    Line, Subproof
}

#[derive(Debug)]
pub enum LineActionKind {
    Insert { what: LAKItem, after: bool, relative_to: LAKItem, },
    Delete { what: LAKItem },
    SetRule { rule: Rule },
    Select,
    ToggleDependency { dep: frunk::Coproduct<PJRef<P>, frunk::Coproduct<<P as Proof>::SubproofReference, frunk::coproduct::CNil>> },
}

pub enum ProofWidgetMsg {
    Nop,
    LineChanged(PJRef<P>, String),
    LineAction(LineActionKind, PJRef<P>),
    CallOnProof(Box<dyn FnOnce(&P)>),
}

impl fmt::Debug for ProofWidgetMsg {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        use self::ProofWidgetMsg::*;
        match self {
            Nop => f.debug_struct("Nop").finish(),
            LineChanged(r, s) => f.debug_tuple("LineChanged").field(&r).field(&s).finish(),
            LineAction(lak, r) => f.debug_tuple("LineAction").field(&lak).field(&r).finish(),
            CallOnProof(_) => f.debug_struct("CallOnProof").finish(),
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
    fn render_line_num_dep_checkbox(&self, line: Option<usize>, proofref: Coprod!(PJRef<P>, <P as Proof>::SubproofReference)) -> Html {
        let line = match line {
            Some(line) => line.to_string(),
            None => "".to_string(),
        };
        if let Some(selected_line) = self.selected_line {
            use frunk::Coproduct::{Inl, Inr};
            if let Inr(Inl(_)) = selected_line {
                let dep = proofref.clone();
                let selected_line_ = selected_line.clone();
                let toggle_dep = self.link.callback(move |_| {
                    ProofWidgetMsg::LineAction(LineActionKind::ToggleDependency { dep }, selected_line_)
                });
                if self.prf.can_reference_dep(&selected_line, &proofref) {
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
    ///   + `pjref` - reference to the justification line containing this menu
    ///   + `cur_rule_name` - name of the current selected rule
    ///
    /// [lib]: https://github.com/vsn4ik/bootstrap-submenu
    fn render_rules_menu(&self, pjref: PJRef<P>, cur_rule_name: &str) -> Html {
        // Create menu items for rule classes
        let menu = RuleClassification::iter()
            .map(|rule_class| {
                // Create menu items for rules in class
                let rules = rule_class
                    .rules()
                    .map(|rule| {
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
                        <div class="dropdown-menu dropdown-scrollbar"> { rules } </div>
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
    fn render_justification_widget(&self, proofref: PJRef<P>) -> Html {
        use frunk::Coproduct::{Inl, Inr};
        if let Inr(Inl(_)) = proofref {
            let lookup_result = self.prf.lookup_pj(&proofref).expect("proofref should exist in self.prf");
            let just: &Justification<_, _, _> = lookup_result.get().expect("proofref already is a JustificationReference");
            let mut dep_lines = String::new();
            for (i, dep) in just.2.iter().enumerate() {
                let (dep_line, _) = self.pud.ref_to_line_depth[&dep];
                dep_lines += &format!("{}{}", dep_line, if i < just.2.len()-1 { ", " } else { "" })
            }
            if just.2.len() > 0 && just.3.len() > 0 {
                dep_lines += "; "
            }
            for (i, sdep) in just.3.iter().enumerate() {
                if let Some(sub) = self.prf.lookup_subproof(&sdep) {
                    let (mut lo, mut hi) = (usize::max_value(), usize::min_value());
                    for line in sub.premises().into_iter().map(Coproduct::inject).chain(sub.direct_lines().into_iter().map(Coproduct::inject)) {
                        if let Some((i, _)) = self.pud.ref_to_line_depth.get(&line) {
                            lo = std::cmp::min(lo, *i);
                            hi = std::cmp::max(hi, *i);
                        }
                    }
                    dep_lines += &format!("{}-{}{}", lo, hi, if i < just.3.len()-1 { ", " } else { "" });
                }
            }

            let cur_rule_name = just.1.get_name();
            let rule_selector = self.render_rules_menu(proofref, &cur_rule_name);
            html! {
                <>
                    <td>
                        { rule_selector }
                    </td>
                    <td>
                        <input type="text" readonly=true value=dep_lines />
                    </td>
                </>
            }
        } else {
            html! {
                <>
                    <td></td>
                    <td></td>
                </>
            }
        }
    }
    fn render_rule_feedback(&self, proofref: PJRef<P>, is_subproof: bool) -> Html {
        use aris::parser::parse;
        let raw_line = match self.pud.ref_to_input.get(&proofref).and_then(|x| if x.len() > 0 { Some(x) } else { None }) {
            None => { return html! { <span></span> }; },
            Some(x) => x,
        };
        match parse(&raw_line).map(|_| self.prf.verify_line(&proofref)) {
            None => html! { <span class="alert alert-warning small-alert">{ "Parse error" }</span> },
            Some(Ok(())) => match proofref {
                Coproduct::Inl(_) => html! {
                    <span class="alert alert-success small-alert">
                        { if is_subproof { "Assumption" } else { "Premise" } }
                    </span>
                },
                _ => html! { <span class="alert small-alert bg-success text-white">{ "Correct" }</span> },
            },
            Some(Err(err)) => {
                html! {
                    <>
                        <button type="button" class="btn btn-danger" data-toggle="popover" data-content=err>
                            { "Error" }
                        </button>
                        <script>
                            { "$('[data-toggle=popover]').popover()" }
                        </script>
                    </>
                }
            },
        }
    }
    fn render_proof_line(&self, line: usize, depth: usize, proofref: PJRef<P>, edge_decoration: &str) -> Html {
        let line_num_dep_checkbox = self.render_line_num_dep_checkbox(Some(line), frunk::Coproduct::inject(proofref.clone()));
        let mut indentation = yew::virtual_dom::VList::new();
        for _ in 0..depth {
            //indentation.add_child(html! { <span style="background-color:black">{"-"}</span>});
            //indentation.add_child(html! { <span style="color:white">{"-"}</span>});
            indentation.add_child(html! { <span class="indent"> { box_chars::VERT } </span>});
        }
        indentation.add_child(html! { <span class="indent">{edge_decoration}</span>});
        let proofref_ = proofref.clone();
        let handle_action = self.link.callback(move |e: ChangeData| {
            if let ChangeData::Select(s) = e {
                let value = s.value();
                s.set_selected_index(0);
                match &*value {
                    "delete_line" => ProofWidgetMsg::LineAction(LineActionKind::Delete { what: LAKItem::Line }, proofref_.clone()),
                    "delete_subproof" => ProofWidgetMsg::LineAction(LineActionKind::Delete { what: LAKItem::Subproof }, proofref_.clone()),
                    "insert_line_before_line" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Line, after: false, relative_to: LAKItem::Line }, proofref_.clone()),
                    "insert_line_after_line" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Line, after: true, relative_to: LAKItem::Line }, proofref_.clone()),
                    "insert_line_before_subproof" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Line, after: false, relative_to: LAKItem::Subproof }, proofref_.clone()),
                    "insert_line_after_subproof" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Line, after: true, relative_to: LAKItem::Subproof }, proofref_.clone()),
                    "insert_subproof_before_line" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Subproof, after: false, relative_to: LAKItem::Line }, proofref_.clone()),
                    "insert_subproof_after_line" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Subproof, after: true, relative_to: LAKItem::Line }, proofref_.clone()),
                    "insert_subproof_before_subproof" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Subproof, after: false, relative_to: LAKItem::Subproof }, proofref_.clone()),
                    "insert_subproof_after_subproof" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Subproof, after: true, relative_to: LAKItem::Subproof }, proofref_.clone()),
                    _ => ProofWidgetMsg::Nop,
                }
            } else {
                ProofWidgetMsg::Nop
            }
        });
        let proofref_ = proofref.clone();
        let handle_input = self.link.callback(move |value: String| ProofWidgetMsg::LineChanged(proofref_.clone(), value));
        let proofref_ = proofref.clone();
        let select_line = self.link.callback(move |()| ProofWidgetMsg::LineAction(LineActionKind::Select, proofref_.clone()));
        let action_selector = {
            use frunk::Coproduct::{Inl, Inr};
            let mut options = yew::virtual_dom::VList::new();
            if may_remove_line(&self.prf, &proofref) {
                options.add_child(html! { <option value="delete_line">{ "Delete line" }</option> });
            }
            if let Some(_) = self.prf.parent_of_line(&pj_to_pjs::<P>(proofref.clone())) {
                // only allow deleting non-root subproofs
                options.add_child(html! { <option value="delete_subproof">{ "Delete subproof" }</option> });
            }
            match proofref {
                Inl(_) => {
                    options.add_child(html! { <option value="insert_line_before_line">{ "Insert premise before this premise" }</option> });
                    options.add_child(html! { <option value="insert_line_after_line">{ "Insert premise after this premise" }</option> });
                },
                Inr(Inl(_)) => {
                    options.add_child(html! { <option value="insert_line_before_line">{ "Insert step before this step" }</option> });
                    options.add_child(html! { <option value="insert_line_after_line">{ "Insert step after this step" }</option> });
                    // Only show subproof creation relative to justification lines, since it may confuse users to have subproofs appear after all the premises when they selected a premise
                    options.add_child(html! { <option value="insert_subproof_before_line">{ "Insert subproof before this step" }</option> });
                    options.add_child(html! { <option value="insert_subproof_after_line">{ "Insert subproof after this step" }</option> });
                },
                Inr(Inr(void)) => match void {},
            }
            html! {
            <select onchange=handle_action>
                <option value="Action">{ "Action" }</option>
                <hr />
                { options }
                //<option value="insert_line_before_subproof">{ "insert_line_before_subproof" }</option>
                //<option value="insert_line_after_subproof">{ "insert_line_after_subproof" }</option>
                //<option value="insert_subproof_before_subproof">{ "insert_subproof_before_subproof" }</option>
                //<option value="insert_subproof_after_subproof">{ "insert_subproof_after_subproof" }</option>
            </select>
            }
        };
        let justification_widget = self.render_justification_widget(proofref.clone());
        let init_value = self.pud.ref_to_input.get(&proofref).cloned().unwrap_or_default();
        let in_subproof = depth > 0;
        let is_selected_line = self.selected_line == Some(proofref);
        let is_dep_line = match self.selected_line {
            Some(Coproduct::Inr(Coproduct::Inl(selected_line))) => {
                match self.prf.lookup_justification_or_die(&selected_line) {
                    Ok(Justification(_, _, line_deps, _)) => line_deps.contains(&proofref),
                    Err(_) => false,
                }
            }
            _ => false,
        };
        let class = if is_selected_line {
            "proof-line table-info"
        } else if is_dep_line {
            "proof-line table-secondary"
        } else {
            "proof-line"
        };
        html! {
            <tr class=class>
                <td> { line_num_dep_checkbox } </td>
                <td>
                    { indentation }
                    <ExprEntry
                        oninput=handle_input
                        onfocus=select_line
                        init_value=init_value />
                </td>
                <td> { self.render_rule_feedback(proofref, in_subproof) } </td>
                { justification_widget }
                <td>{ action_selector }</td>
            </tr>
        }
    }

    fn render_proof(&self, prf: &<P as Proof>::Subproof, sref: Option<<P as Proof>::SubproofReference>, line: &mut usize, depth: &mut usize) -> Html {
        // output has a bool tag to prune subproof spacers with, because VNode's PartialEq doesn't do the right thing
        let mut output: Vec<(Html, bool)> = Vec::new();
        for prem in prf.premises().iter() {
            let edge_decoration = { box_chars::VERT }.to_string();
            output.push((self.render_proof_line(*line, *depth, Coproduct::inject(prem.clone()), &edge_decoration), false));
            *line += 1;
        }
        let dep_checkbox = match sref {
            Some(sr) => self.render_line_num_dep_checkbox(None, frunk::Coproduct::inject(sr)),
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
            use frunk::Coproduct::{Inl, Inr};
            let edge_decoration = if i == prf_lines.len()-1 { box_chars::UP_RIGHT } else { box_chars::VERT }.to_string();
            match lineref {
                Inl(r) => { output.push((self.render_proof_line(*line, *depth, Coproduct::inject(r.clone()), &edge_decoration), false)); *line += 1; },
                Inr(Inl(sr)) => {
                    *depth += 1;
                    //output.push(row_spacer.clone());
                    output.push((self.render_proof(&prf.lookup_subproof(&sr).unwrap(), Some(*sr), line, depth), false));
                    //output.push(row_spacer.clone());
                    *depth -= 1;
                },
                Inr(Inr(void)) => { match *void {} },
            }
        }
        // collapse 2 consecutive row spacers to just 1, formed by adjacent suproofs
        // also remove spacers at the end of an output (since that only occurs if a subproof is the last line of another subproof)
        // This can't be replaced with a range-based loop, since output.len() changes on removal
        {
            let mut i = 0;
            while i < output.len() {
                if output[i].1 && ((i == output.len()-1) || output[i+1].1) {
                    output.remove(i);
                }
                i += 1;
            }
        }
        let output: Vec<Html> = output.into_iter().map(|(x,_)| x).collect();
        let output = yew::virtual_dom::VList::new_with_children(output, None);
        if *depth == 0 {
            html! { <table>{ output }</table> }
        } else {
            yew::virtual_dom::VNode::from(output)
        }
    }
}

fn may_remove_line<P: Proof>(prf: &P, proofref: &PJRef<P>) -> bool {
    use frunk::Coproduct::{Inl, Inr};
    let is_premise = match prf.lookup_pj(proofref) {
        Some(Inl(_)) => true,
        Some(Inr(Inl(_))) => false,
        Some(Inr(Inr(void))) => match void {},
        None => panic!("prf.lookup failed in while processing a Delete"),
    };
    let parent = prf.parent_of_line(&pj_to_pjs::<P>(proofref.clone()));
    match parent.and_then(|x| prf.lookup_subproof(&x)) {
        Some(sub) => (is_premise && sub.premises().len() > 1) || (!is_premise && sub.lines().len() > 1),
        None => (is_premise && prf.premises().len() > 1) || (!is_premise && prf.lines().len() > 1)
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

/// Create a new empty proof, the default proof shown in the UI
fn new_empty_proof() -> P {
    use aris::expression::expression_builders::var;
    let mut proof = P::new();
    proof.add_premise(var(""));
    proof.add_step(Justification(var(""), RuleM::Reit, vec![], vec![]));
    proof
}

impl Component for ProofWidget {
    type Message = ProofWidgetMsg;
    type Properties = ProofWidgetProps;
    fn create(props: Self::Properties, link: ComponentLink<Self>) -> Self {
        props.oncreate.emit(link.clone());
        let (prf, error) = match &props.data {
            Some(data) => {
                let result = aris::proofs::xml_interop::proof_from_xml::<P, _>(&data[..]);
                match result {
                    Ok((prf, _)) => (prf, None),
                    Err(err) => (new_empty_proof(), Some(err)),
                }
            }
            None => (new_empty_proof(), None),
        };

        let pud = ProofUiData::from_proof(&prf);
        let mut tmp = Self {
            link,
            prf,
            pud,
            selected_line: None,
            open_error: error,
            preblob: "".into(),
            props,
        };
        tmp.update(ProofWidgetMsg::Nop);
        tmp
    }
    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        let mut ret = false;
        if self.props.verbose {
            self.preblob += &format!("{:?}\n", msg);
            ret = true;
        }
        use frunk::Coproduct::{Inl, Inr};
        match msg {
            ProofWidgetMsg::Nop => {},
            ProofWidgetMsg::LineChanged(r, input) => {
                self.pud.ref_to_input.insert(r.clone(), input.clone());
                if let Some(e) = aris::parser::parse(&input) {
                    match r {
                        Inl(pr) => { self.prf.with_mut_premise(&pr, |x| { *x = e }); },
                        Inr(Inl(jr)) => { self.prf.with_mut_step(&jr, |x| { x.0 = e }); },
                        Inr(Inr(void)) => match void {},
                    }
                }
                ret = true;
            },
            ProofWidgetMsg::LineAction(LineActionKind::Insert { what, after, relative_to }, orig_ref) => {
                use aris::expression::expression_builders::var;
                let to_select;
                let insertion_point = match relative_to {
                    LAKItem::Line => orig_ref,
                    LAKItem::Subproof => {
                        // TODO: need to refactor Proof::add_*_relative to take Coprod!(Reference, SubproofReference)
                        return ret;
                    },
                };
                match what {
                    LAKItem::Line => match insertion_point {
                        Inl(pr) => { to_select = Inl(self.prf.add_premise_relative(var("__js_ui_blank_premise"), &pr, after)); },
                        Inr(Inl(jr)) => { to_select = Inr(Inl(self.prf.add_step_relative(Justification(var("__js_ui_blank_step"), RuleM::Reit, vec![], vec![]), &jr, after))); },
                        Inr(Inr(void)) => match void {},
                    },
                    LAKItem::Subproof => {
                        let sr = self.prf.add_subproof_relative(&insertion_point.get().unwrap(), after);
                        to_select = self.prf.with_mut_subproof(&sr, |sub| {
                            let to_select = Inl(sub.add_premise(var("__js_ui_blank_premise")));
                            sub.add_step(Justification(var("__js_ui_blank_step"), RuleM::Reit, vec![], vec![]));
                            to_select
                        }).unwrap();
                    },
                }
                self.selected_line = Some(to_select);
                self.preblob += &format!("{:?}\n", self.prf.premises());
                ret = true;
            },
            ProofWidgetMsg::LineAction(LineActionKind::Delete { what }, proofref) => {
                let parent = self.prf.parent_of_line(&pj_to_pjs::<P>(proofref.clone()));
                match what {
                    LAKItem::Line => {
                        fn remove_line_if_allowed<P: Proof, Q: Proof<PremiseReference=<P as Proof>::PremiseReference, JustificationReference=<P as Proof>::JustificationReference>>(prf: &mut Q, pud: &mut ProofUiData<P>, proofref: PJRef<Q>) {
                            if may_remove_line(prf, &proofref) {
                                pud.ref_to_line_depth.remove(&proofref);
                                pud.ref_to_input.remove(&proofref);
                                prf.remove_line(&proofref);
                            }
                        }
                        match parent {
                            Some(sr) => { let pud = &mut self.pud; self.prf.with_mut_subproof(&sr, |sub| { remove_line_if_allowed(sub, pud, proofref); }); },
                            None => { remove_line_if_allowed(&mut self.prf, &mut self.pud, proofref); },
                        }
                    },
                    LAKItem::Subproof => {
                        // TODO: recursively clean out the ProofUiData entries for lines inside a subproof before deletion
                        match parent {
                            Some(sr) => { self.prf.remove_subproof(&sr); },
                            None => {}, // shouldn't delete the root subproof
                        }
                    },
                }
                // Deselect current line to prevent it from pointing to a
                // deleted line. The selected line could be deep inside a
                // deleted subproof, so it's easier to deselect conservatively
                // than to figure out if the selected line is deleted.
                self.selected_line = None;
                ret = true;
            },
            ProofWidgetMsg::LineAction(LineActionKind::SetRule { rule }, proofref) => {
                if let Inr(Inl(jr)) = &proofref {
                    self.prf.with_mut_step(&jr, |j| { j.1 = rule });
                }
                self.selected_line = Some(proofref);
                ret = true;
            },
            ProofWidgetMsg::LineAction(LineActionKind::Select, proofref) => {
                self.selected_line = Some(proofref);
                ret = true;
            },
            ProofWidgetMsg::LineAction(LineActionKind::ToggleDependency { dep }, proofref) => {
                if let Inr(Inl(jr)) = &proofref {
                    self.prf.with_mut_step(&jr, |j| {
                        fn toggle_dep_or_sdep<T: Ord>(dep: T, deps: &mut Vec<T>) {
                            let mut dep_set: BTreeSet<T> = mem::replace(deps, vec![]).into_iter().collect();
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
            },
            ProofWidgetMsg::CallOnProof(f) => {
                f(&self.prf);
            },
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
