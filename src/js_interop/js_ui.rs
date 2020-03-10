extern crate yew;
use yew::prelude::*;
use expression::Expr;
use rules::RuleM;
use proofs::{Proof, Justification, pooledproof::PooledProof};
use std::collections::HashSet;
use std::iter::FromIterator;

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

pub struct ProofWidgetLine {
    link: ComponentLink<Self>,
    props: ProofWidgetLineProps,
}

#[derive(Clone, Properties)]
pub struct ProofWidgetLineProps {
    proofref: <P as Proof>::Reference,
    parent: ComponentLink<ProofWidget>,
    depth: usize,
    line: usize,
}

impl Component for ProofWidgetLine {
    type Message = ();
    type Properties = ProofWidgetLineProps;
    fn create(props: Self::Properties, link: ComponentLink<Self>) -> Self {
        Self {
            link,
            props,
        }
    }
    fn update(&mut self, _: Self::Message) -> ShouldRender {
        false
    }
    fn view(&self) -> Html {
        let mut prefix = format!("{} ({:?}): ", self.props.line, self.props.proofref);
        for _ in 0..(self.props.depth+1) {
            prefix += "|";
        }
        let r1 = self.props.proofref.clone();
        let r2 = self.props.proofref.clone();
        let handle_action = self.props.parent.callback(move |e: ChangeData| {
            if let ChangeData::Select(s) = e {
                let i = s.selected_index();
                s.set_selected_index(0);
                match i {
                    1 => ProofWidgetMsg::LineAction(LineActionKind::InsertBefore, r1.clone()),
                    2 => ProofWidgetMsg::LineAction(LineActionKind::InsertAfter, r1.clone()),
                    _ => ProofWidgetMsg::Nop,
                }
            } else {
                ProofWidgetMsg::Nop
            }
        });
        html! {
            <div>
                { prefix }
                <select onchange=handle_action>
                    <option value="Action">{ "Action" }</option>
                    <hr />
                    <option value="insert_before">{ "insert_before" }</option>
                    <option value="insert_after">{ "insert_after" }</option>
                </select>
<ExprEntry initial_contents="" onchange=self.props.parent.callback(move |e| ProofWidgetMsg::LineChanged(r2.clone(), e)) />
                <br />
            </div>
        }
    }
}

pub struct ProofWidget {
    link: ComponentLink<Self>,
    prf: P,
    preblob: String,
    props: ProofWidgetProps,
}

#[derive(Debug)]
pub enum LineActionKind {
    InsertBefore,
    InsertAfter,
}

#[derive(Debug)]
pub enum ProofWidgetMsg {
    Nop,
    LineChanged(<P as Proof>::Reference, (String, Option<Expr>)),
    LineAction(LineActionKind, <P as Proof>::Reference),
}

#[derive(Clone, Properties)]
pub struct ProofWidgetProps {
    verbose: bool,
}

impl ProofWidget {
    pub fn render_proof(&self, prf: &<P as Proof>::Subproof, line: &mut usize, depth: &mut usize) -> Html {
        let mut output = yew::virtual_dom::VList::new();
        for prem in prf.premises() {
            output.add_child(html! { <ProofWidgetLine parent=self.link.clone() proofref=prem.clone() depth=*depth line=*line/> });
            *line += 1;
        }
        output.add_child(html! { <pre>{ "-----" }</pre> });
        for lineref in prf.lines() {
            use frunk::Coproduct::{Inl, Inr};
            match lineref {
                Inl(r) => { output.add_child(html! { <ProofWidgetLine parent=self.link.clone() proofref=r.clone() depth=*depth line=*line /> }); *line += 1; },
                Inr(Inl(sr)) => { *depth += 1; output.add_child(self.render_proof(&prf.lookup_subproof(sr).unwrap(), line, depth)); *depth -= 1; },
                Inr(Inr(void)) => { match void {} },
            }
        }
        html! {
            <div>
            { output }
            </div>
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
            prf,
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
            ProofWidgetMsg::LineChanged(r, (_, Some(e))) => {
                match r {
                    Inl(_) => self.prf.with_mut_premise(&r, |x| { *x = e }),
                    Inr(Inl(_)) => self.prf.with_mut_step(&r, |x| { x.0 = e }),
                    Inr(Inr(void)) => match void {},
                };
                ret = true;
            },
            ProofWidgetMsg::LineChanged(_, (_, None)) => {},
            ProofWidgetMsg::LineAction(action, r) => {
                use expression_builders::var;
                let after = match action { LineActionKind::InsertBefore => false, LineActionKind::InsertAfter => true };
                match r {
                    Inl(_) => { self.prf.add_premise_relative(var("__js_ui_blank_premise"), r, after); },
                    Inr(Inl(_)) => { self.prf.add_step_relative(Justification(var("__js_ui_blank_step"), RuleM::Reit, vec![], vec![]), r, after); },
                    Inr(Inr(void)) => match void {},
                }
                self.preblob += &format!("{:?}\n", self.prf.premises());
                ret = true;
            },
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
