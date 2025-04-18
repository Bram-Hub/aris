use yew::{html::Scope, prelude::*};
use web_sys::window;

pub struct TabbedContainer {
    tabs: Vec<(String, Html)>,
    current_tab: usize,
}

pub enum TabbedContainerMsg {
    Switch(usize),
    Create { name: String, content: Html },
    Close(usize),
    GetCurrent(Box<dyn FnOnce(usize, String)>),
}

#[derive(Clone, Properties, PartialEq)]
pub struct TabbedContainerProps {
    pub tab_ids: Vec<String>,
    pub children: Children,
    pub oncreate: Callback<Scope<TabbedContainer>>,
    pub onclose: Option<Callback<String>>,
}

impl Component for TabbedContainer {
    type Message = TabbedContainerMsg;
    type Properties = TabbedContainerProps;

    fn create(ctx: &Context<Self>) -> Self {
        let tabs: Vec<(String, Html)> = ctx.props().tab_ids.iter().cloned().zip(ctx.props().children.iter()).collect();
        ctx.props().oncreate.emit(ctx.link().clone());
        Self { tabs, current_tab: 0 }
    }

    fn update(&mut self, ctx: &Context<Self>, msg: Self::Message) -> bool {
        match msg {
            TabbedContainerMsg::Switch(idx) => {
                self.current_tab = idx;
                true
            }
            TabbedContainerMsg::Create { name, content } => {
                self.tabs.insert(0, (name, content));
                // Switch to new tab
                self.current_tab = 0;
                true
            }
            TabbedContainerMsg::Close(idx) => {
                // Grab the name before removal
                let (name, _) = self.tabs.remove(idx);
                // fire the onclose callback if provided
                if let Some(cb) = &ctx.props().onclose {
                    cb.emit(name.clone());
                }
                // adjust current_tab if out of bounds
                if self.current_tab >= self.tabs.len() {
                    self.current_tab = self.tabs.len().saturating_sub(1);
                }
                true
            }
            TabbedContainerMsg::GetCurrent(f) => {
                f(self.current_tab, self.tabs[self.current_tab].0.clone());
                false
            }
        }
    }

    fn changed(&mut self, _: &Context<Self>, _: &Self::Properties) -> bool {
        false
    }

    fn view(&self, ctx: &Context<Self>) -> Html {
        let mut tab_links = yew::virtual_dom::VList::new();
        let mut out = yew::virtual_dom::VList::new();

        for (i, (name, content)) in self.tabs.iter().enumerate() {
            let switch = ctx.link().callback(move |_| TabbedContainerMsg::Switch(i));

            // Wrap the close in a confirm dialog
            let close = {
                let link = ctx.link().clone();
                let name = name.clone();
                Callback::from(move |_| {
                    // ask the user for confirmation
                    let msg = format!("Close tab \"{}\"? You will lose all unsaved changes.", name);
                    if window()
                        .expect("no global `window`")
                        .confirm_with_message(&msg)
                        .unwrap_or(false)
                    {
                        link.send_message(TabbedContainerMsg::Close(i));
                    }
                })
            };
            let link_class = if i == self.current_tab { "nav-link active" } else { "nav-link" };

            tab_links.add_child(html! {
                <li class="nav-item d-flex align-items-center">
                    <a class={ link_class } href="#" onclick={ switch.clone() }>
                        { name }
                    </a>
                    <button
                        type="button"
                        class="close ml-1"
                        aria-label="Close"
                        onclick={ close }>
                        <span aria-hidden="true">{ "×" }</span>
                    </button>
                </li>
            });

            if i == self.current_tab {
                out.add_child(html! { <div> { content.clone() } </div> });
            } else {
                out.add_child(html! { <div style="display:none"> { content.clone() } </div> });
            }
        }

        html! {
            <div>
                <ul class="nav nav-pills"> { tab_links } </ul>
                { out }
            </div>
        }
    }
}
