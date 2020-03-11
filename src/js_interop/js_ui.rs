extern crate yew;
use yew::prelude::*;
use expression::Expr;
use rules::{Rule, RuleM, RuleT};
use proofs::{Proof, Justification, pooledproof::PooledProof};
use std::collections::HashMap;

pub struct ExprEntry {
    link: ComponentLink<Self>,
    current_input: String,
    last_good_parse: String,
    current_expr: Option<Expr>,
    onchange: Callback<(String, Option<Expr>)>,
}

#[derive(Clone, Properties)]
pub struct ExprEntryProps {
    pub initial_contents: String,
    pub onchange: Callback<(String, Option<Expr>)>,
}

impl Component for ExprEntry {
    type Message = String;
    type Properties = ExprEntryProps;
    fn create(props: Self::Properties, link: ComponentLink<Self>) -> Self {
        let mut ret = Self {
            link,
            current_expr: None,
            current_input: props.initial_contents.clone(),
            last_good_parse: "".into(),
            onchange: props.onchange,
        };
        ret.update(props.initial_contents);
        ret
    }
    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        use parser::parse;
        self.current_expr = parse(&*msg);
        if let Some(expr) = &self.current_expr {
            self.last_good_parse = format!("{}", expr);
        }
        self.onchange.emit((self.last_good_parse.clone(), self.current_expr.clone()));
        true
    }
    fn view(&self) -> Html {
        html! {
            <input type="text" oninput=self.link.callback(|e: InputData| e.value) style="width:400px" value={ &self.current_input } />
        }
    }
}

// yew doesn't seem to allow Components to be generic over <P: Proof>, so fix a proof type P at the module level
pub type P = PooledProof<Hlist![Expr]>;

pub struct ProofWidget {
    link: ComponentLink<Self>,
    prf: P,
    ref_to_line_depth: HashMap<<P as Proof>::Reference, (usize, usize)>,
    ref_to_input: HashMap<<P as Proof>::Reference, String>,
    selected_line: Option<<P as Proof>::Reference>,
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
    SetRule { rule: Rule },
    Select,
}

#[derive(Debug)]
pub enum ProofWidgetMsg {
    Nop,
    LineChanged(<P as Proof>::Reference, String),
    LineAction(LineActionKind, <P as Proof>::Reference),
}

#[derive(Clone, Properties)]
pub struct ProofWidgetProps {
    verbose: bool,
}

impl ProofWidget {
    pub fn render_justification_widget(&self, line: usize, depth: usize, proofref: <P as Proof>::Reference) -> Html {
        /* TODO: does HTML/do browsers have a way to do nested menus?
        https://developer.mozilla.org/en-US/docs/Web/HTML/Element/menu is 
        "experimental", and currently firefox only, and a bunch of tutorials for the 
        DDG query "javascript nested context menus" build their own menus out of 
        {div,nav,ul,li} with CSS for displaying the submenus on hover */ 
        use frunk::Coproduct::{Inl, Inr};
        if let Inr(Inl(_)) = proofref {
            let r1 = proofref.clone();
            let handle_rule_select = self.link.callback(move |e: ChangeData| {
                if let ChangeData::Select(s) = e {
                    if let Some(rule) = RuleM::from_serialized_name(&s.value()) {
                        return ProofWidgetMsg::LineAction(LineActionKind::SetRule { rule }, r1);
                    }
                }
                ProofWidgetMsg::Nop
            });
            let mut rules = yew::virtual_dom::VList::new();
            for rule in RuleM::ALL_RULES {
                // TODO: seperators and submenus by RuleClassification
                rules.add_child(html!{ <option value=RuleM::to_serialized_name(*rule)> { rule.get_name() } </option> });
            }
            html! {
                <td>
                <select onchange=handle_rule_select>
                    <option value="no_rule_selected">{"Rule"}</option>
                    <hr />
                    { rules }
                </select>
                </td>
            }
        } else {
            yew::virtual_dom::VNode::from(yew::virtual_dom::VList::new())
        }
    }
    pub fn render_proof_line(&self, line: usize, depth: usize, proofref: <P as Proof>::Reference) -> Html {
        let selection_indicator =
            if self.selected_line == Some(proofref.clone()) {
                html! { <span style="background-color: cyan; color: blue"> { ">" } </span> }
            } else {
                yew::virtual_dom::VNode::from(yew::virtual_dom::VList::new())
            };
        let dep_checkbox = (|| {
            if let Some(selected_line) = self.selected_line{
                use frunk::Coproduct::{Inl, Inr};
                if let Inr(Inl(_)) = selected_line {
                    let (s_line, s_depth) = self.ref_to_line_depth[&selected_line];
                    if line < s_line && depth <= s_depth {
                        return html! { <input type="checkbox"></input> };
                    }
                }
            }
            yew::virtual_dom::VNode::from(yew::virtual_dom::VList::new())
        })();
        let lineinfo = format!("{} ({:?})", line, proofref);
        let mut indentation = yew::virtual_dom::VList::new();
        for _ in 0..(depth+1) {
            indentation.add_child(html! { <span style="background-color:black">{"-"}</span>});
            indentation.add_child(html! { <span style="color:white">{"-"}</span>});
        }
        let r1 = proofref.clone();
        let handle_action = self.link.callback(move |e: ChangeData| {
            if let ChangeData::Select(s) = e {
                let value = s.value();
                s.set_selected_index(0);
                match &*value {
                    "insert_line_before_line" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Line, after: false, relative_to: LAKItem::Line }, r1.clone()),
                    "insert_line_after_line" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Line, after: true, relative_to: LAKItem::Line }, r1.clone()),
                    "insert_line_before_subproof" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Line, after: false, relative_to: LAKItem::Subproof }, r1.clone()),
                    "insert_line_after_subproof" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Line, after: true, relative_to: LAKItem::Subproof }, r1.clone()),
                    "insert_subproof_before_line" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Subproof, after: false, relative_to: LAKItem::Line }, r1.clone()),
                    "insert_subproof_after_line" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Subproof, after: true, relative_to: LAKItem::Line }, r1.clone()),
                    "insert_subproof_before_subproof" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Subproof, after: false, relative_to: LAKItem::Subproof }, r1.clone()),
                    "insert_subproof_after_subproof" => ProofWidgetMsg::LineAction(LineActionKind::Insert { what: LAKItem::Subproof, after: true, relative_to: LAKItem::Subproof }, r1.clone()),
                    _ => ProofWidgetMsg::Nop,
                }
            } else {
                ProofWidgetMsg::Nop
            }
        });
        let r2 = proofref.clone();
        let handle_input = self.link.callback(move |e: InputData| ProofWidgetMsg::LineChanged(r2.clone(), e.value.clone()));
        let r3 = proofref.clone();
        let select_line = self.link.callback(move |_| ProofWidgetMsg::LineAction(LineActionKind::Select, r3.clone()));
        let action_selector = html! {
            <select onchange=handle_action>
                <option value="Action">{ "Action" }</option>
                <hr />
                <option value="insert_line_before_line">{ "insert_line_before_line" }</option>
                <option value="insert_line_after_line">{ "insert_line_after_line" }</option>
                //<option value="insert_line_before_subproof">{ "insert_line_before_subproof" }</option>
                //<option value="insert_line_after_subproof">{ "insert_line_after_subproof" }</option>
                <option value="insert_subproof_before_line">{ "insert_subproof_before_line" }</option>
                <option value="insert_subproof_after_line">{ "insert_subproof_after_line" }</option>
                //<option value="insert_subproof_before_subproof">{ "insert_subproof_before_subproof" }</option>
                //<option value="insert_subproof_after_subproof">{ "insert_subproof_after_subproof" }</option>
            </select>
        };
        let justification_widget = self.render_justification_widget(line, depth, proofref.clone());
        html! {
            <tr>
                <td> { selection_indicator } </td>
                <td> { lineinfo } </td>
                <td> { dep_checkbox } </td>
                <td>
                { indentation }
                { action_selector }
                <input type="text" oninput=handle_input onfocus=select_line style="width:400px" value=self.ref_to_input.get(&proofref).unwrap_or(&String::new()) />
                </td>
                { justification_widget }
            </tr>
        }
    }

    pub fn render_proof(&self, prf: &<P as Proof>::Subproof, line: &mut usize, depth: &mut usize) -> Html {
        let mut output = yew::virtual_dom::VList::new();
        for prem in prf.premises() {
            output.add_child(self.render_proof_line(*line, *depth, prem.clone()));
            *line += 1;
        }
        let mut spacer = yew::virtual_dom::VList::new();
        spacer.add_child(html! { <td></td> });
        spacer.add_child(html! { <td style="background-color:black"></td> });
        output.add_child(yew::virtual_dom::VNode::from(spacer));
        for lineref in prf.lines() {
            use frunk::Coproduct::{Inl, Inr};
            match lineref {
                Inl(r) => { output.add_child(self.render_proof_line(*line, *depth, r.clone())); *line += 1; },
                Inr(Inl(sr)) => { *depth += 1; output.add_child(self.render_proof(&prf.lookup_subproof(sr).unwrap(), line, depth)); *depth -= 1; },
                Inr(Inr(void)) => { match void {} },
            }
        }
        if *depth == 0 {
            html! { <table>{ output }</table> }
        } else {
            yew::virtual_dom::VNode::from(output)
        }
    }

}
pub fn calculate_lineinfo(output: &mut HashMap<<P as Proof>::Reference, (usize, usize)>, prf: &<P as Proof>::Subproof, line: &mut usize, depth: &mut usize) {
    for prem in prf.premises() {
        output.insert(prem.clone(), (*line, *depth));
        *line += 1;
    }
    for lineref in prf.lines() {
        use frunk::Coproduct::{Inl, Inr};
        match lineref {
            Inl(r) => { output.insert(r, (*line, *depth)); *line += 1; },
            Inr(Inl(sr)) => { *depth += 1; calculate_lineinfo(output, &prf.lookup_subproof(sr).unwrap(), line, depth); *depth -= 1; },
            Inr(Inr(void)) => { match void {} },
        }
    }
}

impl Component for ProofWidget {
    type Message = ProofWidgetMsg;
    type Properties = ProofWidgetProps;
    fn create(props: Self::Properties, link: ComponentLink<Self>) -> Self {
        use expression_builders::var;
        let mut prf = P::new();
        prf.add_premise(var("__js_ui_blank_premise"));
        prf.add_step(Justification(var("__js_ui_blank_step"), RuleM::Reit, vec![], vec![]));
        Self {
            link,
            ref_to_line_depth: HashMap::new(),
            ref_to_input: HashMap::new(),
            prf,
            selected_line: None,
            preblob: "".into(),
            props,
        }
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
                self.ref_to_input.insert(r.clone(), input.clone());
                if let Some(e) = crate::parser::parse(&input) {
                    match r {
                        Inl(_) => { self.prf.with_mut_premise(&r, |x| { *x = e }); },
                        Inr(Inl(_)) => { self.prf.with_mut_step(&r, |x| { x.0 = e }); },
                        Inr(Inr(void)) => match void {},
                    }
                }
                ret = true;
            },
            ProofWidgetMsg::LineAction(LineActionKind::Insert { what, after, relative_to }, orig_ref) => {
                use expression_builders::var;
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
                        Inl(_) => { to_select = self.prf.add_premise_relative(var("__js_ui_blank_premise"), insertion_point, after); },
                        Inr(Inl(_)) => { to_select = self.prf.add_step_relative(Justification(var("__js_ui_blank_step"), RuleM::Reit, vec![], vec![]), insertion_point, after); },
                        Inr(Inr(void)) => match void {},
                    },
                    LAKItem::Subproof => {
                        let sr = self.prf.add_subproof_relative(insertion_point, after);
                        to_select = self.prf.with_mut_subproof(&sr, |sub| {
                            let to_select = sub.add_premise(var("__js_ui_blank_premise"));
                            sub.add_step(Justification(var("__js_ui_blank_step"), RuleM::Reit, vec![], vec![]));
                            to_select
                        }).unwrap();
                    },
                }
                self.selected_line = Some(to_select);
                self.preblob += &format!("{:?}\n", self.prf.premises());
                ret = true;
            },
            ProofWidgetMsg::LineAction(LineActionKind::SetRule { rule }, proofref) => {
                self.prf.with_mut_step(&proofref, |j| { j.1 = rule });
                self.selected_line = Some(proofref);
                ret = true;
            },
            ProofWidgetMsg::LineAction(LineActionKind::Select, proofref) => {
                self.selected_line = Some(proofref);
                ret = true;
            },
        }
        if ret {
            calculate_lineinfo(&mut self.ref_to_line_depth, self.prf.top_level_proof(), &mut 1, &mut 0);
        }
        ret
    }
    fn view(&self) -> Html {
        let interactive_proof = self.render_proof(self.prf.top_level_proof(), &mut 1, &mut 0);
        html! {
            <div>
                { interactive_proof }
                <hr />
                <pre> { format!("{}\n{:#?}", self.prf, self.prf) } </pre>
                <hr />
                <pre> { self.preblob.clone() } </pre>
            </div>
        }
    }
}

pub struct App {
    link: ComponentLink<Self>,
    last_good_parse: String,
    current_expr: Option<Expr>,
}

pub enum Msg {
    ExprChanged(String, Option<Expr>),
}

impl Component for App {
    type Message = Msg;
    type Properties = ();

    fn create(_: Self::Properties, link: ComponentLink<Self>) -> Self {
        Self { link, last_good_parse: "".into(), current_expr: None }
    }

    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        match msg {
            Msg::ExprChanged(last_good_parse, current_expr) => {
                self.last_good_parse = last_good_parse;
                self.current_expr = current_expr;
                true
            },
        }
    }

    fn view(&self) -> Html {
        html! {
            <div>
                <p>{ "Enter Expression:" }</p>
                <ExprEntry initial_contents="forall A, ((exists B, A -> B) & C & f(x, y | z)) <-> Q <-> R" onchange=self.link.callback(|(x, y)| Msg::ExprChanged(x, y)) />
                <div>
                    { &self.last_good_parse }
                    <br/>
                    <pre>
                        { self.current_expr.as_ref().map(|e| format!("{:#?}", e)).unwrap_or("Error".into()) }
                    </pre>
                </div>
                <hr />
                <ProofWidget verbose=true />
            </div>
        }
    }
}
