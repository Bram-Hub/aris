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
    depth: usize,
    link: ComponentLink<Self>,
    props: ProofWidgetLineProps,
}

#[derive(Clone, Properties)]
pub struct ProofWidgetLineProps {
    proofref: <P as Proof>::Reference,
    parent: ComponentLink<ProofWidget>,
}

impl Component for ProofWidgetLine {
    type Message = ();
    type Properties = ProofWidgetLineProps;
    fn create(props: Self::Properties, link: ComponentLink<Self>) -> Self {
        Self {
            depth: 0,
            link,
            props,
        }
    }
    fn update(&mut self, _: Self::Message) -> ShouldRender {
        false
    }
    fn view(&self) -> Html {
        let mut prefix = String::new();
        for _ in 0..(self.depth+1) {
            prefix += "|";
        }
        let r = self.props.proofref.clone();
        html! {
            <div>
                { prefix } <ExprEntry initial_contents="" onchange=self.props.parent.callback(move |e| ProofWidgetMsg::LineChanged(r.clone(), e)) />
                <br />
            </div>
        }
    }
}

pub struct ProofWidget {
    link: ComponentLink<Self>,
    prf: P,
    lines: Vec<<P as Proof>::Reference>,
    separator_indices: HashSet<usize>,
}

pub enum ProofWidgetMsg {
    LineChanged(<P as Proof>::Reference, (String, Option<Expr>)),
}

impl Component for ProofWidget {
    type Message = ProofWidgetMsg;
    type Properties = ();
    fn create(_: Self::Properties, link: ComponentLink<Self>) -> Self {
        use expression_builders::var;
        let mut prf = P::new();
        let r1 = prf.add_premise(var("__js_ui_blank_premise"));
        let r2 = prf.add_step(Justification(var("__js_ui_blank_step"), RuleM::Reit, vec![], vec![]));
        Self {
            link,
            prf,
            lines: vec![r1, r2],
            separator_indices: HashSet::from_iter(vec![1]),
        }
    }
    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        match msg {
            ProofWidgetMsg::LineChanged(r, (_, Some(e))) => {
                use frunk::Coproduct::{Inl, Inr};
                match r {
                    Inl(_) => self.prf.with_mut_premise(&r, |x| { *x = e }),
                    Inr(Inl(_)) => self.prf.with_mut_step(&r, |x| { x.0 = e }),
                    Inr(Inr(void)) => match void {},
                };
                return true;
            },
            ProofWidgetMsg::LineChanged(_, (_, None)) => {
            }
        }
        false
    }
    fn view(&self) -> Html {
        let mut children = yew::virtual_dom::VList::new();
        for (i, line) in self.lines.iter().enumerate() {
            if self.separator_indices.contains(&i) {
                children.add_child(html! { <pre>{ "-----" }</pre> });
            }
            children.add_child(html! {
                <ProofWidgetLine parent=self.link.clone() proofref=line.clone() />
            });
        }
        html! {
            <div>
                { children }
                <hr />
                <pre> { format!("{:#?}", self.prf) } </pre>
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
                <ProofWidget />
            </div>
        }
    }
}
