use yew::{html::Scope, prelude::*};

pub struct TabbedContainer {
    tabs: Vec<(String, Html)>,
    current_tab: usize,
}

pub enum TabbedContainerMsg {
    Switch(usize),
    Create { name: String, content: Html },
    GetCurrent(Box<dyn FnOnce(usize, String)>),
}

#[derive(Clone, Properties, PartialEq)]
pub struct TabbedContainerProps {
    pub tab_ids: Vec<String>,
    pub children: Children,
    pub oncreate: Callback<Scope<TabbedContainer>>,
}

impl Component for TabbedContainer {
    type Message = TabbedContainerMsg;
    type Properties = TabbedContainerProps;

    fn create(ctx: &Context<Self>) -> Self {
        let tabs: Vec<(String, Html)> = ctx.props().tab_ids.iter().cloned().zip(ctx.props().children.iter()).collect();
        ctx.props().oncreate.emit(ctx.link().clone());
        Self { tabs, current_tab: 0 }
    }

    fn update(&mut self, _: &Context<Self>, msg: Self::Message) -> bool {
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
        for (i, (name, data)) in self.tabs.iter().enumerate() {
            let onclick = ctx.link().callback(move |_| TabbedContainerMsg::Switch(i));
            let link_class = if i == self.current_tab { "nav-link active" } else { "nav-link" };
            tab_links.add_child(html! {
                <li class="nav-item">
                    <a class={ link_class } href="#" onclick={ onclick }>
                        { name }
                    </a>
                </li>
            });
            if i == self.current_tab {
                out.add_child(html! { <div> { data.clone() } </div> });
            } else {
                out.add_child(html! { <div style="display:none"> { data.clone() } </div> });
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
