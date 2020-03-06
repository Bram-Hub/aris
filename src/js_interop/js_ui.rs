extern crate yew;
use yew::prelude::*;
use expression::Expr;

pub struct App {
    link: ComponentLink<Self>,
    string_expr: String,
    last_good_parse: String,
    test_expr: Option<Expr>
}

pub enum Msg {
    TestExpression(String),
}

impl Component for App {
    type Message = Msg;
    type Properties = ();

    fn create(_: Self::Properties, link: ComponentLink<Self>) -> Self {
        use parser::parse;
        let initial_expr = "forall A, ((exists B, A -> B) & C & f(x, y | z)) <-> Q <-> R";
        let test_expr = parse(initial_expr);
        let last_good_parse = format!("{}", test_expr.as_ref().unwrap());
        Self {
            link,
            string_expr: initial_expr.into(),
            test_expr,
            last_good_parse,
        }
    }

    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        match msg {
            Msg::TestExpression(data) => {
                use parser::parse;
                self.test_expr = parse(&*data);
                if let Some(expr) = &self.test_expr {
                    self.last_good_parse = format!("{}", expr);
                }
                true
            },
        }
    }

    fn view(&self) -> Html {
        html! {
            <div>
                <p>{ "Enter Expression:" }</p>
                <input type="text" oninput=self.link.callback(|e: InputData| Msg::TestExpression(e.value)) style="width:400px" value={ &self.string_expr } />
                <div>
                    { &self.last_good_parse }
                    <br/>
                    <pre>
                        { self.test_expr.as_ref().map(|e| format!("{:#?}", e)).unwrap_or("Error".into()) }
                    </pre>
                </div>
            </div>
        }
    }
}
