<template>
  <div class="page docs-page">
    <TitleBlock :title="t('docs.title')" :sheet="t('docs.sheet')" :sheet-label="t('common.appendix')">
      {{ t('docs.subtitle') }}
    </TitleBlock>

    <section class="docs-index" :aria-label="t('docs.documents')">
      <article v-for="doc in docs" :key="doc.href" class="doc-row">
        <component :is="doc.icon" class="doc-icon" aria-hidden="true" />
        <div class="doc-text">
          <h2>{{ t(`docs.${doc.key}Title`) }}</h2>
          <p>{{ t(`docs.${doc.key}Body`) }}</p>
        </div>
        <a :href="doc.href" target="_blank" rel="noopener" class="btn btn--sm doc-link">
          {{ t(`docs.${doc.key}Link`) }}
          <ExternalLink aria-hidden="true" />
          <span class="sr-only">{{ t('common.opensNewTab') }}</span>
        </a>
      </article>
    </section>

    <section class="block notes" aria-labelledby="automation-heading">
      <div class="block-head">
        <h2 id="automation-heading">{{ t('docs.automation') }}</h2>
      </div>
      <ul>
        <i18n-t scope="global" keypath="docs.automationCi" tag="li">
          <template #path><code>public/docs/</code></template>
        </i18n-t>
        <i18n-t scope="global" keypath="docs.automationOpenapi" tag="li">
          <template #file><code>openapi.json</code></template>
        </i18n-t>
        <li>{{ t('docs.automationManual') }}</li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
// Documentation overview page
import { useI18n } from 'vue-i18n'
import { FileJson, BookOpen, Gauge, ClipboardCheck, ExternalLink } from 'lucide-vue-next'
import TitleBlock from '@/components/pad/TitleBlock.vue'

const { t } = useI18n()

// 문서 제목·설명·링크 문구는 docs.{key}Title/Body/Link 키에서 가져온다
const docs = [
  { key: 'openapi', href: '/docs/openapi.json', icon: FileJson },
  { key: 'javadoc', href: '/docs/apidocs/index.html', icon: BookOpen },
  { key: 'coverage', href: '/docs/coverage/index.html', icon: Gauge },
  { key: 'tests', href: '/docs/tests/index.html', icon: ClipboardCheck }
]
</script>

<style scoped>
.docs-index {
  border-top: 2px solid var(--rule-strong);
}

.doc-row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: var(--space-5);
  align-items: center;
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--rule);
}

.doc-icon {
  width: 24px;
  height: 24px;
}

.doc-text h2 {
  margin-bottom: var(--space-1);
}

.doc-text p {
  margin: 0;
  color: var(--ink-2);
}

.doc-link svg {
  width: 16px;
  height: 16px;
}

.notes ul {
  padding-left: var(--space-5);
  display: grid;
  gap: var(--space-2);
}

.notes {
  margin-top: var(--space-12);
}

@media (max-width: 640px) {
  .doc-row {
    grid-template-columns: 24px minmax(0, 1fr);
  }

  .doc-link {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
