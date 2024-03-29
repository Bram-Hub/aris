use yew::prelude::*;

/// A text field for entering expressions
pub struct ExprEntry {
    /// Reference to `<input>` node
    node_ref: NodeRef,
}

/// Message sent to `ExprEntry`
pub enum ExprEntryMsg {
    /// Text field was edited
    OnEdit,

    /// Text field was focused
    OnFocus,
}

/// Properties for `ExprEntry`
#[derive(Clone, Properties, PartialEq)]
pub struct ExprEntryProps {
    /// Callback to call when text field is changed, with the first parameter
    /// being the new text
    pub oninput: Callback<String>,

    /// Callback to call when text field is focused
    #[prop_or_default]
    pub onfocus: Option<Callback<()>>,

    /// Whether the text field should be focused
    ///
    /// ## Values:
    ///   * `None` - The default, don't automatically focus or unfocus.
    ///   * `Some(false)` - Automatically unfocus text field after each render.
    ///   * `Some(true)` - Automatically focus text field after each render.
    #[prop_or_default]
    pub focus: Option<bool>,

    /// Initial text in text field when it is loaded
    pub init_value: String,

    /// An ID to use for our strings
    pub id: String,
}

impl Component for ExprEntry {
    type Message = ExprEntryMsg;
    type Properties = ExprEntryProps;
    fn create(_: &Context<Self>) -> Self {
        Self { node_ref: NodeRef::default() }
    }
    fn update(&mut self, ctx: &Context<Self>, msg: Self::Message) -> bool {
        match msg {
            ExprEntryMsg::OnEdit => {
                self.handle_edit(ctx);
                false
            }
            ExprEntryMsg::OnFocus => {
                if let Some(onfocus) = &ctx.props().onfocus {
                    onfocus.emit(())
                }
                false
            }
        }
    }
    fn changed(&mut self, _: &Context<Self>, _: &Self::Properties) -> bool {
        true
    }
    fn view(&self, ctx: &Context<Self>) -> Html {
        html! {
            <input
                ref={ self.node_ref.clone() }
                type="text"
                id={ ctx.props().id.clone() }
                class="form-control text-input-custom"
                oninput={ ctx.link().callback(|_| ExprEntryMsg::OnEdit) }
                onfocus={ ctx.link().callback(|_| ExprEntryMsg::OnFocus) }
                value={ ctx.props().init_value.clone() } />
        }
    }
    fn rendered(&mut self, ctx: &Context<Self>, _first_render: bool) {
        self.update_focus(ctx)
    }
}

impl ExprEntry {
    /// Get `<input>` element used as a text field
    fn input_element(&self) -> web_sys::HtmlInputElement {
        self.node_ref.cast::<web_sys::HtmlInputElement>().expect("failed casting node ref to input element")
    }

    /// Sync the focus of the text field with the `focus` property
    fn update_focus(&self, ctx: &Context<Self>) {
        let input = self.input_element();

        match ctx.props().focus {
            Some(true) => input.focus().expect("failed focusing expr entry"),
            Some(false) => input.blur().expect("failed unfocusing expr entry"),
            None => {}
        }
    }

    /// Handle an edit of the expression text field by expanding macros with
    /// `aris::macros::expand()`. To preserve the cursor position, the strings
    /// to the left and right of the cursor are expanded separately.
    fn handle_edit(&self, ctx: &Context<Self>) {
        let input_elem = self.input_element();

        // Get cursor position in text field
        let cursor_pos = input_elem.selection_start().expect("failed getting selection start").unwrap_or_default() as usize;

        // Get text to the left and right of cursor position
        //
        // NOTE: The cursor position is measured in characters, not bytes, so
        // the `String` must be converted to `Vec<char>`.
        let value = input_elem.value().chars().collect::<Vec<char>>();
        let (left, right) = value.split_at(cursor_pos);

        // Convert left and right text back into regular `Strings` and expand
        // macros
        let left = left.iter().collect::<String>();
        let left = aris::macros::expand(&left);
        let right = right.iter().collect::<String>();
        let right = aris::macros::expand(&right);

        // Compute new cursor position
        let cursor_pos = left.chars().count() as u32;

        // Update text field value
        let value = [left, right].concat();
        input_elem.set_value(&value);

        // Update cursor position
        input_elem.set_selection_start(Some(cursor_pos)).expect("failed setting selection start");
        input_elem.set_selection_end(Some(cursor_pos)).expect("failed setting selection end");

        ctx.props().oninput.emit(value);
    }
}
