<template>
  <section class="option-return-page">
    <header class="page-header">
      <h1>Option Return</h1>
      <p>
        Build the position list first. Expiry return calculations can be layered on top of these saved rows next.
      </p>
    </header>

    <option-position-list />
  </section>
</template>

<script>
import OptionPositionList from '../components/option-return/OptionPositionList.vue'

export default {
  name: 'OptionReturn',
  components: {
    OptionPositionList
  }
}
</script>

<style scoped>
.option-return-page {
  max-width: 1080px;
  margin: 1rem auto;
  padding: 0 0.9rem 1rem;
}

.page-header {
  margin-bottom: 0.75rem;
}

.page-header h1 {
  margin: 0 0 0.25rem;
  color: #0f4c81;
  font-size: 1.4rem;
}

.page-header p {
  margin: 0;
  color: #52606d;
  font-size: 0.88rem;
}
</style>
