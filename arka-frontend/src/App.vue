<template>
  <nav>
    <RouterLink class="brand" to="/">ARKA B2B</RouterLink>
    <RouterLink to="/">Dashboard</RouterLink>
    <RouterLink to="/products">Productos</RouterLink>
    <RouterLink to="/cart">Carrito</RouterLink>
  </nav>
  <main>
    <RouterView />
  </main>
</template>
