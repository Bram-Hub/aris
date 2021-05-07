use crate::components::nav_bar::NavBarMsg;
use crate::components::nav_bar::NavBarWidget;
use crate::components::proof_widget::ProofWidget;
use crate::components::proof_widget::ProofWidgetMsg;
use crate::components::tabbed_container::TabbedContainer;
use crate::components::tabbed_container::TabbedContainerMsg;
use crate::util::P;

use std::collections::HashMap;

use yew::prelude::*;

pub struct App {
    link: ComponentLink<Self>,
    tabcontainer_link: Option<ComponentLink<TabbedContainer>>,
    proofs: HashMap<String, ComponentLink<ProofWidget>>,
}

pub enum AppMsg {
    TabbedContainerInit(ComponentLink<TabbedContainer>),
    NavBarInit(ComponentLink<NavBarWidget>),
    CreateTab { name: String, content: Html },
    RegisterProofName { name: String, link: ComponentLink<ProofWidget> },
    GetProofFromCurrentTab(Box<dyn FnOnce(String, &P)>),
}

impl Component for App {
    type Message = AppMsg;
    type Properties = ();

    fn create(_: Self::Properties, link: ComponentLink<Self>) -> Self {
        Self { link, tabcontainer_link: None, proofs: HashMap::new() }
    }

    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        match msg {
            AppMsg::TabbedContainerInit(tabcontainer_link) => {
                self.tabcontainer_link = Some(tabcontainer_link);
                false
            }
            AppMsg::NavBarInit(menuwidget_link) => {
                // create the first blank proof tab
                menuwidget_link.send_message(NavBarMsg::FileNew);
                false
            }
            AppMsg::CreateTab { name, content } => {
                if let Some(tabcontainer_link) = &self.tabcontainer_link {
                    tabcontainer_link.send_message(TabbedContainerMsg::CreateTab { name, content });
                }
                true
            }
            AppMsg::RegisterProofName { name, link } => {
                self.proofs.insert(name, link);
                false
            }
            AppMsg::GetProofFromCurrentTab(f) => {
                if let Some(tabcontainer_link) = &self.tabcontainer_link {
                    let proofs = self.proofs.clone();
                    tabcontainer_link.send_message(TabbedContainerMsg::GetCurrentTab(Box::new(move |_, name| {
                        if let Some(link) = proofs.get(&*name) {
                            link.send_message(ProofWidgetMsg::CallOnProof(Box::new(move |prf| f(name, prf))));
                        }
                    })));
                }
                false
            }
        }
    }

    fn change(&mut self, _: Self::Properties) -> ShouldRender {
        false
    }

    fn view(&self) -> Html {
        let resolution_fname: String = "resolution_example.bram".into();
        let resolution_fname_ = resolution_fname.clone();
        let tabview = html! {
            <TabbedContainer tab_ids=vec![resolution_fname.clone(), "Parser demo".into()] oncreate=self.link.callback(|link| AppMsg::TabbedContainerInit(link))>
                <ProofWidget verbose=true data=Some(include_bytes!("../../../example-proofs/resolution_example.bram").to_vec()) oncreate=self.link.callback(move |link| AppMsg::RegisterProofName { name: resolution_fname_.clone(), link }) />
            </TabbedContainer>
        };
        html! {
            <div>
                <NavBarWidget parent=self.link.clone() oncreate=self.link.callback(|link| AppMsg::NavBarInit(link)) />
                { tabview }
            </div>
        }
    }
}
