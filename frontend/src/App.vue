<template>
  <router-view />
</template>

<style>
* {
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
  margin: 0;
  font-family: 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
  /* 与 theme.css 保持一致，避免两处各自定义底色 */
  background-color: var(--c-page-bg);
}
</style>
