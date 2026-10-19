<template>
  <div class="markdown-body" v-html="html" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import MarkdownIt from 'markdown-it';

const props = defineProps<{ text: string }>();
// Model output is untrusted text. Keep raw HTML disabled, including during streaming.
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: false, typographer: false });
markdown.renderer.rules.link_open = (tokens, index, options, _env, renderer) => {
  tokens[index]!.attrSet('target', '_blank');
  tokens[index]!.attrSet('rel', 'noopener noreferrer');
  return renderer.renderToken(tokens, index, options);
};
const html = computed(() => markdown.render(props.text));
</script>

<style scoped>
.markdown-body {
  margin-top: 5px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: normal;
}
.markdown-body :deep(p) {
  margin: 0 0 8px;
}
.markdown-body :deep(> :last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(strong) {
  font-weight: 700;
  color: #e5f3ff;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0;
  padding-left: 22px;
}
.markdown-body :deep(li + li) {
  margin-top: 4px;
}
.markdown-body :deep(li > p) {
  margin: 4px 0;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin: 12px 0 6px;
  font-size: 1.08em;
  line-height: 1.5;
  color: #e5f3ff;
}
.markdown-body :deep(code) {
  font-family: Consolas, monospace;
  background: #061c31;
  border-radius: 3px;
  padding: 1px 4px;
}
.markdown-body :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  margin: 8px 0;
  padding: 10px;
  background: #061c31;
  border: 1px solid #245276;
  border-radius: 4px;
}
.markdown-body :deep(pre code) {
  padding: 0;
  white-space: pre;
  overflow-wrap: normal;
}
.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding-left: 10px;
  border-left: 3px solid #297ba9;
  color: #a8c6df;
}
.markdown-body :deep(a) {
  color: #5cdcf3;
  text-decoration: underline;
}
.markdown-body :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  margin: 8px 0;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 5px 8px;
  border: 1px solid #245276;
  text-align: left;
}
.markdown-body :deep(hr) {
  border: 0;
  border-top: 1px solid #245276;
  margin: 12px 0;
}
.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
}
</style>
