import { createRoot } from "react-dom/client";
import { HashRouter } from "react-router";
import App from "./App";
import "./styles.css";

const rootElement = document.getElementById("root");
if (!rootElement) {
  throw new Error("找不到前端根节点 #root");
}

createRoot(rootElement).render(
  <HashRouter>
    <App />
  </HashRouter>,
);
