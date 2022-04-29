use crate::components::app::App;
use crate::components::app::AppMsg;
use crate::components::expr_ast_widget::ExprAstWidget;
use crate::components::proof_widget::ProofWidget;

use gloo::timers::callback::Timeout;
use wasm_bindgen::{closure::Closure, JsCast, JsValue};
use yew::prelude::*;
use yew_octicons::Icon;
use yew_octicons::IconKind;

pub struct FileOpenHelper {
    file_open_closure: Closure<dyn FnMut(JsValue)>,
    filename_tx: std::sync::mpsc::Sender<(String, web_sys::FileReader)>,
}

impl FileOpenHelper {
    fn new(parent: ComponentLink<App>) -> Self {
        let (filename_tx, filename_rx) = std::sync::mpsc::channel::<(String, web_sys::FileReader)>();
        let file_open_closure = Closure::wrap(Box::new(move |_| {
            if let Ok((fname, reader)) = filename_rx.recv() {
                if let Ok(contents) = reader.result() {
                    if let Some(contents) = contents.as_string() {
                        let fname_ = fname.clone();
                        let oncreate = parent.callback(move |link| AppMsg::RegisterProofName { name: fname_.clone(), link });
                        parent.send_message(AppMsg::CreateTab { name: fname, content: html! { <ProofWidget verbose=true data=Some(contents.into_bytes()) oncreate=oncreate /> } });
                    }
                }
            }
        }) as Box<dyn FnMut(JsValue)>);
        Self { file_open_closure, filename_tx }
    }
    fn fileopen(&mut self, file_list: web_sys::FileList) -> ShouldRender {
        if let Some(file) = file_list.get(0) {
            // MDN (https://developer.mozilla.org/en-US/docs/Web/API/Blob/text) and web-sys (https://docs.rs/web-sys/0.3.36/web_sys/struct.Blob.html#method.text)
            // both document "Blob.text()" as being a thing, but both chrome and firefox say that "getObject(...).text is not a function"
            /*let _ = self.filename_tx.send(file.name());
            file.dyn_into::<web_sys::Blob>().expect("dyn_into::<web_sys::Blob> failed").text().then(&self.file_open_closure);*/
            let reader = web_sys::FileReader::new().expect("FileReader");
            reader.set_onload(Some(self.file_open_closure.as_ref().unchecked_ref()));
            reader.read_as_text(&file).expect("FileReader::read_as_text");
            let _ = self.filename_tx.send((file.name(), reader));
        }
        true
    }
}

pub struct NavBarWidget {
    link: ComponentLink<Self>,
    props: NavBarProps,
    node_ref: NodeRef,
    next_tab_idx: usize,
    file_open_helper: FileOpenHelper,
}

pub enum NavBarMsg {
    FileNew,
    FileOpen(web_sys::FileList),
    FileSave,
    NewExprTree,
    ToggleTheme,
    Nop,
}
#[derive(Properties, Clone)]
pub struct NavBarProps {
    pub parent: ComponentLink<App>,
    pub oncreate: Callback<ComponentLink<NavBarWidget>>,
}

impl Component for NavBarWidget {
    type Message = NavBarMsg;
    type Properties = NavBarProps;

    fn create(props: Self::Properties, link: ComponentLink<Self>) -> Self {
        props.oncreate.emit(link.clone());
        let file_open_helper = FileOpenHelper::new(props.parent.clone());
        Self { link, props, node_ref: NodeRef::default(), next_tab_idx: 1, file_open_helper }
    }

    fn update(&mut self, msg: Self::Message) -> ShouldRender {
        match msg {
            NavBarMsg::FileNew => {
                let fname = format!("Untitled proof {}", self.next_tab_idx);
                let fname_ = fname.clone();
                let oncreate = self.props.parent.callback(move |link| AppMsg::RegisterProofName { name: fname_.clone(), link });
                self.props.parent.send_message(AppMsg::CreateTab { name: fname, content: html! { <ProofWidget verbose=true data=None oncreate=oncreate /> } });
                self.next_tab_idx += 1;
                false
            }
            NavBarMsg::FileOpen(file_list) => self.file_open_helper.fileopen(file_list),
            NavBarMsg::FileSave => {
                let node = self.node_ref.get().expect("NavBarWidget::node_ref failed");
                self.props.parent.send_message(AppMsg::GetProofFromCurrentTab(Box::new(move |name, prf| {
                    use aris::proofs::xml_interop;
                    let mut data = vec![];
                    let metadata = xml_interop::ProofMetaData { author: Some("ARIS-YEW-UI".into()), hash: None, goals: vec![] };
                    xml_interop::xml_from_proof_and_metadata_with_hash(prf, &metadata, &mut data).expect("xml_from_proof_and_metadata failed");
                    let window = web_sys::window().expect("web_sys::window failed");
                    let document = window.document().expect("window.document failed");
                    let anchor = document.create_element("a").expect("document.create_element(\"a\") failed");
                    let anchor = anchor.dyn_into::<web_sys::HtmlAnchorElement>().expect("dyn_into::HtmlAnchorElement failed");
                    anchor.set_download(&name);
                    let js_str = JsValue::from_str(&String::from_utf8_lossy(&data));
                    let js_array = js_sys::Array::new_with_length(1);
                    js_array.set(0, js_str);
                    let blob = web_sys::Blob::new_with_str_sequence(&js_array).expect("Blob::new_with_str_sequence failed");
                    let url = web_sys::Url::create_object_url_with_blob(&blob).expect("Url::create_object_url_with_blob failed");
                    anchor.set_href(&url);
                    node.append_child(&anchor).expect("node.append_child failed");
                    anchor.click();
                    Timeout::new(0, move || {
                        node.remove_child(&anchor).expect("node.remove_child failed");
                    })
                    .forget();
                })));
                false
            }
            NavBarMsg::NewExprTree => {
                self.props.parent.send_message(AppMsg::CreateTab {
                    name: format!("Expr Tree {}", self.next_tab_idx),
                    content: html! {
                        <ExprAstWidget initial_contents="forall A, ((exists B, A -> B) & C & f(x, y | z)) <-> Q <-> R" />
                    },
                });
                self.next_tab_idx += 1;
                false
            }
            NavBarMsg::ToggleTheme => {
                match theme().as_str() {
                    "light" => document_element().set_attribute("theme", "dark").expect("failed setting dark theme"),
                    "dark" => document_element().set_attribute("theme", "light").expect("failed setting light theme"),
                    theme => unreachable!("unknown theme {}", theme),
                }
                true
            }
            NavBarMsg::Nop => false,
        }
    }

    fn change(&mut self, props: Self::Properties) -> ShouldRender {
        self.props = props;
        true
    }

    fn view(&self) -> Html {
        let handle_open_file = self.link.callback(move |e| if let ChangeData::Files(file_list) = e { NavBarMsg::FileOpen(file_list) } else { NavBarMsg::Nop });

        let file_menu = html! {
            <ul class="navbar-nav">
                <li ref=self.node_ref.clone() class="nav-item dropdown show">
                    <a class="nav-link dropdown-toggle" href="#" role="button" id="dropdownMenuLink" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">{"File"}</a>
                    <div class="dropdown-menu" aria-labelledby="dropdownMenuLink">
                        <div>
                            <label for="file-menu-new-proof" class="dropdown-item">{"New blank proof"}</label>
                            <input id="file-menu-new-proof" style="display:none" type="button" onclick=self.link.callback(|_| NavBarMsg::FileNew) />
                        </div>
                        <div>
                            <label for="file-menu-open-proof" class="dropdown-item">{"Open proof"}</label>
                            <input id="file-menu-open-proof" style="display:none" type="file" onchange=handle_open_file />
                        </div>
                        <div>
                            <label for="file-menu-save-proof" class="dropdown-item">{"Save proof"}</label>
                            <input id="file-menu-save-proof" style="display:none" type="button" onclick=self.link.callback(|_| NavBarMsg::FileSave) />
                        </div>
                        <div>
                            <label for="file-menu-new-expr-tree" class="dropdown-item">{"New expression tree"}</label>
                            <input id="file-menu-new-expr-tree" style="display:none" type="button" onclick=self.link.callback(|_| NavBarMsg::NewExprTree) />
                        </div>
                    </div>
                </li>
            </ul>
        };

        let theme_icon_kind = match theme().as_str() {
            "light" => IconKind::Sun,
            "dark" => IconKind::Moon,
            theme => unreachable!("unknown theme {}", theme),
        };

        let navbar = html! {
            // Bootstrap navbar
            // https://getbootstrap.com/docs/4.5/components/navbar/
            <nav class="navbar navbar-expand-lg navbar-dark bg-secondary">
                // Navbar brand
                <a class="navbar-brand" href="#"> { "Aris" } </a>

                { file_menu }

                <ul class="navbar-nav ml-auto">
                    // Theme toggle
                    <li class="nav-item">
                        <a class="nav-link" onclick=self.link.callback(|_| NavBarMsg::ToggleTheme)>
                            { Icon::new_big(theme_icon_kind) }
                        </a>
                    </li>
                    // Help menu
                    <li class="nav-item">
                        <a class="nav-link" data-toggle="modal" data-target="#help-modal">
                            { Icon::new_big(IconKind::Question) }
                        </a>
                    </li>
                </ul>
            </nav>
        };

        html! {
            <>
                { navbar }
                { render_help_modal() }
            </>
        }
    }
}

/// Shortcut for `window.document.documentElement`, panicing on error
fn document_element() -> web_sys::Element {
    let window = web_sys::window().expect("window()");
    let document = window.document().expect("window.document()");
    document.document_element().expect("document.document_element()")
}

/// Get the name of the current theme, or panic if the theme attribute doesn't exist.
fn theme() -> String {
    document_element().get_attribute("theme").expect("failed querying theme")
}

fn render_help_modal() -> Html {
    html! {
        <div class="modal fade" id="help-modal" tabindex="-1" role="dialog" aria-labelledby="help-modal-label" aria-hidden="true">
            <div class="modal-dialog" role="document">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="help-modal-label"> { "Aris Help" } </h5>
                        <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                            <span aria-hidden="true"> { '×' } </span>
                        </button>
                    </div>
                    <div class="modal-body">
                        { render_help_body() }
                    </div>
                </div>
            </div>
        </div>
    }
}

fn render_help_body() -> Html {
    // Maximum amount of macros for any symbol
    let max_col_span = aris::macros::TABLE.iter().map(|(_, macros)| macros.len()).max().unwrap_or_default();
    let table_rows = aris::macros::TABLE
        .iter()
        .map(|(symbol, macros)| {
            // Convert row to HTML
            let macros = macros
                .iter()
                .chain(std::iter::repeat(&""))
                .take(max_col_span)
                .map(|macro_| {
                    html! {
                        <td> { macro_ } </td>
                    }
                })
                .collect::<Vec<Html>>();
            html! {
                <tr>
                    <td> { symbol } </td>
                    { macros }
                </tr>
            }
        })
        .collect::<Vec<Html>>();

    html! {
        <>
            <h5> { "Logic symbol macros" } </h5>
            <table class="table table-bordered">
                <thead>
                    <tr>
                        <th> { "Symbol" } </th>
                        <th colspan=max_col_span> { "Macros" } </th>
                    </tr>
                </thead>
                <tbody>
                    { table_rows }
                </tbody>
            </table>
        </>
    }
}
