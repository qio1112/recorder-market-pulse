<template>
  <section class="not-found">
    <h1>404</h1>
    <p>Page Not Found</p>
  </section>
</template>

<script>
export default {
  name: 'PageNotFound'
}
</script>

<style scoped>
.not-found {
  min-height: 70vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: #102a43;
}

h1 {
  margin: 0;
  font-size: 4rem;
  letter-spacing: 0.05em;
}

p {
  margin: 0;
  font-size: 1.25rem;
  color: #52606d;
}
</style>
