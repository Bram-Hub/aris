extern crate yew;
use yew::prelude::*;
use expression::Expr;

pub struct App {
    link: ComponentLink<Self>,
    string_expr: String,
    test_expr: Option<Expr>
}

pub enum Msg {
    TestExpression(String),
}

impl Component for App {
    type Message = Msg;
    type Properties = ();

    fn create(_: Self::Properties, link: ComponentLink<Self>) -> Self {
        Self {
            link,
            string_expr: "".into(),
            test_expr: None
        }
    }

    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        match msg {
            Msg::TestExpression(data) => {
                use parser::parse;
                self.test_expr = parse(&*data);
                true
            },
            _ => false
        }
    }

    fn view(&self) -> Html {
        html! {
            <div>
                <p>{ "Enter Expression:" }</p>
                <textarea value=&self.string_expr oninput=self.link.callback(|e: InputData| Msg::TestExpression(e.value))></textarea>
                <div>
                    { &self.string_expr }
                    <br/>
                    { self.test_expr.as_ref().map(|e| format!("{:?}", e)).unwrap_or("Error".into()) }
                </div>
            </div>
        }
    }
}