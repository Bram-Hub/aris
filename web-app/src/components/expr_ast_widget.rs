use crate::components::expr_entry::ExprEntry;

use aris::expr::Expr;

use yew::prelude::*;

pub struct ExprAstWidget {
    link: ComponentLink<Self>,
    current_input: String,
    last_good_parse: String,
    current_expr: Option<Expr>,
}

#[derive(Clone, Properties)]
pub struct ExprAstWidgetProps {
    pub initial_contents: String,
}

impl Component for ExprAstWidget {
    type Message = String;
    type Properties = ExprAstWidgetProps;
    fn create(props: Self::Properties, link: ComponentLink<Self>) -> Self {
        let mut ret = Self { link, current_expr: None, current_input: props.initial_contents.clone(), last_good_parse: "".into() };
        ret.update(props.initial_contents);
        ret
    }
    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        use aris::parser::parse;
        self.current_input = msg.clone();
        self.current_expr = parse(&*msg);
        if let Some(expr) = &self.current_expr {
            self.last_good_parse = format!("{}", expr);
        }
        true
    }
    fn change(&mut self, _: Self::Properties) -> ShouldRender {
        false
    }
    fn view(&self) -> Html {
        // Convert expression to debug string
        let expr_debug = self.current_expr.as_ref().map(|e| format!("{:#?}", e));

        // Convert debug expression to HTML or parse error
        let expr_debug = match expr_debug {
            Some(s) => {
                html! {
                    <div class="card">
                        <pre> { s } </pre>
                    </div>
                }
            }
            None => {
                html! {
                    <div class="alert alert-danger"> { "Parse error" } </div>
                }
            }
        };

        html! {
            <div class="alert alert-primary m-4">
                <h2> { "Enter Expression:" } </h2>
                <ExprEntry
                    oninput=self.link.callback(|value| value)
                    init_value={ &self.current_input } />
                <hr />
                <h5> { &self.last_good_parse } </h5>
                { expr_debug }
            </div>
        }
    }
}
