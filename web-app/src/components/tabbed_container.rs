use yew::prelude::*;

pub struct TabbedContainer {
    link: ComponentLink<Self>,
    tabs: Vec<(String, Html)>,
    current_tab: usize,
}

pub enum TabbedContainerMsg {
    Switch(usize),
    Create { name: String, content: Html },
    GetCurrent(Box<dyn FnOnce(usize, String)>),
}

#[derive(Clone, Properties)]
pub struct TabbedContainerProps {
    pub tab_ids: Vec<String>,
    pub children: Children,
    pub oncreate: Callback<ComponentLink<TabbedContainer>>,
}

impl Component for TabbedContainer {
    type Message = TabbedContainerMsg;
    type Properties = TabbedContainerProps;

    fn create(props: Self::Properties, link: ComponentLink<Self>) -> Self {
        let tabs: Vec<(String, Html)> = props.tab_ids.into_iter().zip(props.children.into_iter()).collect();
        props.oncreate.emit(link.clone());
        Self { link, tabs, current_tab: 0 }
    }

    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        match msg {
            TabbedContainerMsg::Switch(idx) => {
                self.current_tab = idx;
                true
            }
            TabbedContainerMsg::Create { name, content } => {
                self.tabs.push((name, content));
                // Switch to new tab
                self.current_tab = self.tabs.len() - 1;
                true
            }
            TabbedContainerMsg::GetCurrent(f) => {
                f(self.current_tab, self.tabs[self.current_tab].0.clone());
                false
            }
        }
    }

    fn change(&mut self, _: Self::Properties) -> ShouldRender {
        false
    }

    fn view(&self) -> Html {
        let mut tab_links = yew::virtual_dom::VList::new();
        let mut out = yew::virtual_dom::VList::new();

        for (i, (name, data)) in self.tabs.iter().enumerate() {
            let onclick = self.link.callback(move |_| TabbedContainerMsg::Switch(i));
            let link_class = if i == self.current_tab { "nav-link active" } else { "nav-link" };
            tab_links.add_child(html! {
                <li class="nav-item">
                    <a class=link_class href="#" onclick=onclick>
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
