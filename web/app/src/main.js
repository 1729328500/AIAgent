import { createApp } from "vue";
import App from "./App.vue";
const app = createApp(App);
app.component("Vue3MarkdownIt", (await import("vue3-markdown-it")).default);
app.mount("#app");
