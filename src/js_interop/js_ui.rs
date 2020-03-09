extern crate yew;
use yew::prelude::*;
use expression::Expr;

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
            </div>
        }
    }
}
