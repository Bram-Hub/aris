use crate::components::expr_entry::ExprEntry;

use aris::expression::Expr;

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
        let mut ret = Self {
            link,
            current_expr: None,
            current_input: props.initial_contents.clone(),
            last_good_parse: "".into(),
        };
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
    fn change(&mut self, props: Self::Properties) -> ShouldRender {
        self.update(props.initial_contents);
        true
    }
    fn view(&self) -> Html {
        let expr_debug = self
            .current_expr
            .as_ref()
            .map(|e| format!("{:#?}", e))
            .unwrap_or("Error".into());
        html! {
            <div>
                <h2> {"Enter Expression:"} </h2>
                <ExprEntry
                    oninput=self.link.callback(|value| value)
                    init_value={ &self.current_input } />
                <div>
                    { &self.last_good_parse }
                    <br/>
                    <pre> { expr_debug } </pre>
                </div>
            </div>
        }
    }
}
