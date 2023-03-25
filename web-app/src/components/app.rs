use crate::components::nav_bar::NavBarMsg;
use crate::components::nav_bar::NavBarWidget;
use crate::components::proof_widget::ProofWidget;
use crate::components::proof_widget::ProofWidgetMsg;
use crate::components::tabbed_container::TabbedContainer;
use crate::components::tabbed_container::TabbedContainerMsg;
use crate::util::P;

use std::collections::HashMap;

use yew::html::Scope;
use yew::prelude::*;

pub struct App {
    tabcontainer_link: Option<Scope<TabbedContainer>>,
    proofs: HashMap<String, Scope<ProofWidget>>,
}

pub enum AppMsg {
    TabbedContainerInit(Scope<TabbedContainer>),
    NavBarInit(Scope<NavBarWidget>),
    CreateTab {
        name: String,
        content: Html,
    },
    RegisterProofName {
        name: String,
        link: Scope<ProofWidget>,
    },
    #[allow(clippy::type_complexity)]
    GetProofFromCurrentTab(Box<dyn FnOnce(String, &P)>),
}

impl Component for App {
    type Message = AppMsg;
    type Properties = ();

    fn create(_: &Context<Self>) -> Self {
        Self { tabcontainer_link: None, proofs: HashMap::new() }
    }

    fn update(&mut self, _: &Context<Self>, msg: Self::Message) -> bool {
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
                    tabcontainer_link.send_message(TabbedContainerMsg::Create { name, content });
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
                    tabcontainer_link.send_message(TabbedContainerMsg::GetCurrent(Box::new(move |_, name| {
                        if let Some(link) = proofs.get(&*name) {
                            link.send_message(ProofWidgetMsg::CallOnProof(Box::new(move |prf| f(name, prf))));
                        }
                    })));
                }
                false
            }
        }
    }

    fn changed(&mut self, _: &Context<Self>, _: &Self::Properties) -> bool {
        false
    }

    fn view(&self, ctx: &Context<Self>) -> Html {
        let resolution_fname: String = "resolution_example.bram".into();
        let resolution_fname_ = resolution_fname.clone();
        let tabview = html! {
            <TabbedContainer tab_ids={ vec![resolution_fname, "Parser demo".into()] } oncreate={ ctx.link().callback(AppMsg::TabbedContainerInit) }>
                <ProofWidget verbose=true data={ Some(include_bytes!("../../../example-proofs/resolution_example.bram").to_vec()) } oncreate={ ctx.link().callback(move |link| AppMsg::RegisterProofName { name: resolution_fname_.clone(), link }) } />
            </TabbedContainer>
        };
        html! {
            <div>
                <NavBarWidget parent={ ctx.link().clone() } oncreate={ ctx.link().callback(AppMsg::NavBarInit) } />
                { tabview }
            </div>
        }
    }
}
