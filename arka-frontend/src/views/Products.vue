<template>
  <div>
    <h1>Productos — Catálogo B2B</h1>

    <div class="grid-2">
      <!-- Formulario crear producto -->
      <div class="card">
        <h2>Registrar nuevo producto (HU1)</h2>
        <div v-if="successMsg" class="alert alert-success">{{ successMsg }}</div>
        <div v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</div>
        <form @submit.prevent="createProduct">
          <div>
            <label>Nombre del producto</label>
            <input v-model="form.name" placeholder="Monitor 4K UltraWide" required />
          </div>
          <div>
            <label>Categoría</label>
            <select v-model="form.category">
              <option>MONITORES</option>
              <option>TECLADOS</option>
              <option>PERIFÉRICOS</option>
              <option>SERVIDORES</option>
              <option>REDES</option>
              <option>ALMACENAMIENTO</option>
            </select>
          </div>
          <div>
            <label>Precio base (USD)</label>
            <input v-model.number="form.basePriceUsd" type="number" step="0.01" min="0" required />
          </div>
          <div>
            <label>ID Proveedor</label>
            <input v-model="form.supplier.supplierId" placeholder="sup-001" required />
          </div>
          <div>
            <label>Nombre proveedor</label>
            <input v-model="form.supplier.name" placeholder="TechCorp S.A." required />
          </div>
          <div>
            <label>Lead time (días)</label>
            <input v-model.number="form.supplier.leadTimeDays" type="number" min="1" />
          </div>
          <button class="btn btn-primary" type="submit" :disabled="loading">
            {{ loading ? 'Iniciando saga...' : 'Registrar producto' }}
          </button>
        </form>

        <!-- Saga progress cuando se crea un producto -->
        <div v-if="newProductId" style="margin-top:1rem">
          <h2>Saga HU1 en progreso</h2>
          <div class="flow-diagram" style="flex-direction:column;align-items:flex-start">
            <div class="flow-step" :class="sagaStep >= 1 ? 'done' : ''">
              1. ms-catalog guardó → VALIDANDO_PROVEEDOR ✓
            </div>
            <div style="margin-left:1rem;color:#475569">↓ catalog.product-validated</div>
            <div class="flow-step" :class="sagaStep >= 2 ? 'done' : 'active'">
              2. ms-provider validando...
            </div>
            <div style="margin-left:1rem;color:#475569">↓ provider.provider-validated</div>
            <div class="flow-step" :class="sagaStep >= 3 ? 'done' : ''">
              3. ms-inventory creando stock...
            </div>
            <div style="margin-left:1rem;color:#475569">↓ inventory.stock-created</div>
            <div class="flow-step" :class="sagaStep >= 4 ? 'done' : ''">
              4. ms-catalog → CONFIRMADO ✓
            </div>
          </div>
          <div v-if="finalStatus" style="margin-top:0.5rem">
            Estado final: <span class="badge" :class="statusBadge(finalStatus)">{{ finalStatus }}</span>
          </div>
        </div>
      </div>

      <!-- Lista de productos -->
      <div class="card">
        <h2>Productos registrados</h2>
        <button class="btn btn-primary btn-sm" @click="loadProducts" style="margin-bottom:1rem">
          Actualizar
        </button>
        <div v-if="products.length === 0" class="empty">
          No hay productos aún. Crea el primero →
        </div>
        <table v-else>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Categoría</th>
              <th>Estado</th>
              <th>Precio</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in products" :key="p.productId">
              <td>{{ p.name }}</td>
              <td>{{ p.category }}</td>
              <td><span class="badge" :class="statusBadge(p.status)">{{ p.status }}</span></td>
              <td>${{ p.basePriceUsd }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const products = ref<any[]>([])
const loading = ref(false)
const successMsg = ref('')
const errorMsg = ref('')
const newProductId = ref('')
const sagaStep = ref(0)
const finalStatus = ref('')

const form = ref({
  name: '',
  category: 'MONITORES',
  basePriceUsd: 0,
  supplier: { supplierId: 'sup-001', name: 'TechCorp', leadTimeDays: 14 }
})

const statusBadge = (status: string) => ({
  'VALIDANDO_PROVEEDOR': 'badge-yellow',
  'EN_CREACION_STOCK':   'badge-blue',
  'CONFIRMADO':          'badge-green',
  'RECHAZADO':           'badge-red',
}[status] ?? 'badge-gray')

async function loadProducts() {
  try {
    const res = await fetch('/api/v1/products')
    if (res.ok) products.value = await res.json()
  } catch (_) {}
}

async function createProduct() {
  loading.value = true
  successMsg.value = ''
  errorMsg.value = ''
  sagaStep.value = 0
  newProductId.value = ''
  finalStatus.value = ''

  try {
    const res = await fetch('/api/v1/products', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form.value)
    })

    if (!res.ok) {
      errorMsg.value = `Error ${res.status}: ${await res.text()}`
      return
    }

    const created = await res.json()
    newProductId.value = created.productId
    sagaStep.value = 1
    successMsg.value = `Producto creado (${created.productId.slice(0,8)}...) — Saga iniciada`

    // Polling para ver el progreso de la saga
    await pollSaga(created.productId)
    loadProducts()
  } catch (e) {
    errorMsg.value = 'No se pudo conectar al API Gateway'
  } finally {
    loading.value = false
  }
}

async function pollSaga(productId: string) {
  for (let i = 0; i < 15; i++) {
    await new Promise(r => setTimeout(r, 1000))
    try {
      const res = await fetch(`/api/v1/products/${productId}`)
      if (!res.ok) continue
      const p = await res.json()
      finalStatus.value = p.status
      if (p.status === 'VALIDANDO_PROVEEDOR') sagaStep.value = 1
      if (p.status === 'EN_CREACION_STOCK')   sagaStep.value = 2
      if (p.status === 'CONFIRMADO' || p.status === 'RECHAZADO') {
        sagaStep.value = 4
        break
      }
    } catch (_) {}
  }
}

onMounted(loadProducts)
</script>
