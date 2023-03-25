use crate::components::expr_entry::ExprEntry;

use aris::expr::Expr;

use yew::prelude::*;

pub struct ExprAstWidget {
    current_input: String,
    last_good_parse: String,
    current_expr: Option<Expr>,
}

#[derive(Clone, Properties, PartialEq)]
pub struct ExprAstWidgetProps {
    pub initial_contents: String,
}

impl Component for ExprAstWidget {
    type Message = String;
    type Properties = ExprAstWidgetProps;
    fn create(ctx: &Context<Self>) -> Self {
        let mut ret = Self { current_expr: None, current_input: ctx.props().initial_contents.clone(), last_good_parse: "".into() };
        Component::update(&mut ret, ctx, ctx.props().initial_contents.clone());
        ret
    }
    fn update(&mut self, _: &Context<Self>, msg: Self::Message) -> bool {
        use aris::parser::parse;
        self.current_input = msg.clone();
        self.current_expr = parse(&msg);
        if let Some(expr) = &self.current_expr {
            self.last_good_parse = format!("{expr}");
        }
        true
    }
    fn changed(&mut self, _: &Context<Self>, _: &Self::Properties) -> bool {
        false
    }
    fn view(&self, ctx: &Context<Self>) -> Html {
        // Convert expression to debug string
        let expr_debug = self.current_expr.as_ref().map(|e| format!("{e:#?}"));

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
                    oninput={ ctx.link().callback(|value| value) }
                    init_value={ self.current_input.clone() }
                    id=""/>
                <hr />
                <h5> { &self.last_good_parse } </h5>
                { expr_debug }
            </div>
        }
    }
}
