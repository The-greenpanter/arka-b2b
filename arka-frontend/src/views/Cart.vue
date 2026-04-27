<template>
  <div>
    <h1>Carrito — Saga HU4</h1>

    <div class="grid-2">
      <!-- Crear / gestionar carrito -->
      <div class="card">
        <h2>Crear carrito</h2>
        <div v-if="cart">
          <p style="color:#64748b;margin-bottom:1rem">
            Cart ID: <code>{{ cart.cartId }}</code> — Cliente: <strong>{{ cart.customerId }}</strong>
          </p>

          <h2 style="margin-top:1rem">Agregar ítem</h2>
          <form @submit.prevent="addItem" style="flex-direction:row;flex-wrap:wrap;gap:0.5rem">
            <div style="flex:1;min-width:140px">
              <label>Product ID</label>
              <input v-model="itemForm.productId" placeholder="prod-001" required />
            </div>
            <div style="width:80px">
              <label>Cantidad</label>
              <input v-model.number="itemForm.qty" type="number" min="1" value="1" />
            </div>
            <div style="width:100px">
              <label>Precio unit.</label>
              <input v-model.number="itemForm.price" type="number" step="0.01" min="0" />
            </div>
            <div style="align-self:flex-end">
              <button class="btn btn-primary btn-sm" type="submit">Agregar</button>
            </div>
          </form>

          <table v-if="cart.items?.length" style="margin-top:1rem">
            <thead><tr><th>Producto</th><th>Cant.</th><th>Precio</th><th></th></tr></thead>
            <tbody>
              <tr v-for="item in cart.items" :key="item.productId">
                <td>{{ item.productId }}</td>
                <td>{{ item.quantity }}</td>
                <td>${{ item.unitPriceUsd }}</td>
                <td>
                  <button class="btn btn-danger btn-sm" @click="removeItem(item.productId)">✕</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty" style="padding:1rem">Sin ítems</div>

          <div style="margin-top:1rem;display:flex;gap:0.75rem">
            <button class="btn btn-primary" @click="checkout" :disabled="!cart.items?.length">
              Checkout → Crear pedido
            </button>
          </div>

          <div v-if="orderId" class="alert alert-success" style="margin-top:1rem">
            Pedido creado: <code>{{ orderId }}</code><br/>
            Saga HU4 iniciada: inventory.stock-reserved → payment.payment-processed → order.order-completed
          </div>
        </div>

        <form v-else @submit.prevent="createCart">
          <div>
            <label>Customer ID</label>
            <input v-model="customerForm.customerId" placeholder="cust-001" required />
          </div>
          <button class="btn btn-primary" type="submit">Crear carrito</button>
        </form>
      </div>

      <!-- Saga HU4 info -->
      <div class="card">
        <h2>Flujo Saga HU4 — Checkout</h2>
        <div class="flow-diagram" style="flex-direction:column;align-items:flex-start;gap:0.75rem">
          <div class="flow-step" :class="checkoutStep >= 1 ? 'done' : ''">
            1. ms-cart → checkout-requested
          </div>
          <div style="margin-left:1rem;color:#475569;font-size:0.8rem">↓ cart.checkout-requested</div>
          <div class="flow-step" :class="checkoutStep >= 2 ? 'done' : ''">
            2. ms-order → crea Order(PENDING) → order-created
          </div>
          <div style="margin-left:1rem;color:#475569;font-size:0.8rem">↓ order.order-created</div>
          <div class="flow-step" :class="checkoutStep >= 3 ? 'done' : ''">
            3. ms-inventory → reserva stock → stock-reserved
          </div>
          <div style="margin-left:1rem;color:#475569;font-size:0.8rem">↓ inventory.stock-reserved</div>
          <div class="flow-step" :class="checkoutStep >= 4 ? 'done' : ''">
            4. ms-payment → procesa pago → payment-processed
          </div>
          <div style="margin-left:1rem;color:#475569;font-size:0.8rem">↓ payment.payment-processed</div>
          <div class="flow-step" :class="checkoutStep >= 5 ? 'done' : ''">
            5. ms-order → COMPLETED
          </div>
        </div>

        <div v-if="errorMsg" class="alert alert-error" style="margin-top:1rem">{{ errorMsg }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const cart = ref<any>(null)
const orderId = ref('')
const errorMsg = ref('')
const checkoutStep = ref(0)

const customerForm = ref({ customerId: 'cust-001' })
const itemForm = ref({ productId: '', qty: 1, price: 0 })

async function createCart() {
  try {
    const res = await fetch('/api/v1/carts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ customerId: customerForm.value.customerId })
    })
    if (res.ok) cart.value = await res.json()
    else errorMsg.value = `Error ${res.status}`
  } catch (_) {
    errorMsg.value = 'No se pudo conectar al API Gateway'
  }
}

async function addItem() {
  try {
    const res = await fetch(`/api/v1/carts/${cart.value.cartId}/items`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        productId: itemForm.value.productId,
        quantity: itemForm.value.qty,
        unitPriceUsd: itemForm.value.price
      })
    })
    if (res.ok) cart.value = await res.json()
  } catch (_) {}
}

async function removeItem(productId: string) {
  try {
    const res = await fetch(`/api/v1/carts/${cart.value.cartId}/items/${productId}`, { method: 'DELETE' })
    if (res.ok) cart.value = await res.json()
  } catch (_) {}
}

async function checkout() {
  checkoutStep.value = 1
  orderId.value = ''
  try {
    const res = await fetch(`/api/v1/carts/${cart.value.cartId}/checkout`, { method: 'POST' })
    if (res.ok) {
      const data = await res.json()
      orderId.value = data.orderId ?? cart.value.cartId
      checkoutStep.value = 2
    } else {
      errorMsg.value = `Checkout falló: ${res.status}`
    }
  } catch (_) {
    errorMsg.value = 'No se pudo conectar al API Gateway'
  }
}
</script>
