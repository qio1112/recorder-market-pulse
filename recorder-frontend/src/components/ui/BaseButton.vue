<template>
  <button v-if="!link" :class="mode">
    <slot></slot>
  </button>
  <router-link v-else :to="to" :class="mode">
    <slot></slot>
  </router-link>
</template>

<script>
export default {
  props: {
    mode: {
      type: String,
      required: false,
      default: null
    },
    link: {
      type: Boolean,
      required: false,
      default: false
    },
    to: {
      type: String,
      required: false,
      default: '/'
    }
  }
}
</script>

<style scoped>
button,
a {
  text-decoration: none;
  padding: 0.3rem 0.8rem;
  font: 0.3rem;
  background-color: #00768d;
  border: 1px solid #00768d;
  color: white;
  cursor: pointer;
  border-radius: 4px;
  margin-right: 0rem;
  display: inline-block;
}

a:hover,
a:active,
button:hover,
button:active {
  background-color: #1c826a;
  border-color: #1c826a;
}

.flat {
  background-color: transparent;
  color: #00768d;
  border: none;
}

.outline {
  background-color: transparent;
  border-color: #00768d;
  color: #00768d;
}

.flat:hover,
.flat:active,
.outline:hover,
.outline:active {
  background-color: #63e6c8;
}
</style>