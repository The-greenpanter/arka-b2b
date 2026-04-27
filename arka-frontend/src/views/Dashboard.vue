<template>
  <div>
    <h1>Dashboard — ARKA B2B</h1>

    <div class="grid-3" style="margin-bottom:1.5rem">
      <div class="stat-card">
        <div class="number">{{ stats.totalProducts }}</div>
        <div class="label">Productos</div>
      </div>
      <div class="stat-card">
        <div class="number">{{ stats.confirmed }}</div>
        <div class="label">Confirmados</div>
      </div>
      <div class="stat-card">
        <div class="number">{{ stats.pending }}</div>
        <div class="label">En proceso</div>
      </div>
    </div>

    <div class="card">
      <h2>Saga HU1 — Crear producto (flujo coreografiado)</h2>
      <div class="flow-diagram">
        <div class="flow-step done">ms-catalog<br/><small>Registra producto</small></div>
        <span class="arrow">→ catalog.product-validated →</span>
        <div class="flow-step done">ms-provider<br/><small>Valida proveedor</small></div>
        <span class="arrow">→ provider.provider-validated →</span>
        <div class="flow-step done">ms-inventory<br/><small>Crea stock</small></div>
        <span class="arrow">→ inventory.stock-created →</span>
        <div class="flow-step done">ms-catalog<br/><small>CONFIRMADO</small></div>
      </div>
    </div>

    <div class="card">
      <h2>Microservicios activos</h2>
      <table>
        <thead>
          <tr>
            <th>Servicio</th>
            <th>Puerto</th>
            <th>Base de datos</th>
            <th>Rol en sagas</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ms in services" :key="ms.name">
            <td>{{ ms.name }}</td>
            <td><code>:{{ ms.port }}</code></td>
            <td>{{ ms.db }}</td>
            <td>{{ ms.role }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="card">
      <h2>Infraestructura cloud</h2>
      <div class="tag-container">
        <span class="badge badge-blue">Kafka KRaft :9092</span>
        <span class="badge badge-blue">MongoDB :27017</span>
        <span class="badge badge-blue">PostgreSQL :5432</span>
        <span class="badge badge-yellow">LocalStack :4566 (AWS sim)</span>
        <span class="badge badge-green">API Gateway :8080</span>
        <span class="badge badge-gray">Kafka UI :8090</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const stats = ref({ totalProducts: 0, confirmed: 0, pending: 0 })

const services = [
  { name: 'ms-catalog',       port: 8081, db: 'MongoDB',    role: 'HU1 origin, HU2' },
  { name: 'ms-inventory',     port: 8082, db: 'PostgreSQL', role: 'HU1 stock, HU4 reserva' },
  { name: 'ms-provider',      port: 8085, db: 'PostgreSQL', role: 'HU1 validación' },
  { name: 'ms-cart',          port: 8086, db: 'MongoDB',    role: 'HU4 entrada' },
  { name: 'ms-order',         port: 8083, db: 'PostgreSQL', role: 'HU4 orquestador' },
  { name: 'ms-payment',       port: 8084, db: 'PostgreSQL', role: 'HU4 cobro' },
  { name: 'ms-notifications', port: 8087, db: '—',          role: 'Alertas multi-evento' },
  { name: 'ms-reporter',      port: 8088, db: 'PostgreSQL', role: 'CQRS event store' },
  { name: 'api-gateway',      port: 8080, db: '—',          role: 'Entrada única' },
]

onMounted(async () => {
  try {
    const res = await fetch('/api/v1/products')
    if (res.ok) {
      const products = await res.json()
      stats.value.totalProducts = products.length
      stats.value.confirmed = products.filter((p: any) => p.status === 'CONFIRMADO').length
      stats.value.pending = products.filter((p: any) => p.status !== 'CONFIRMADO').length
    }
  } catch (_) {
    // VPS no disponible — stats quedan en 0
  }
})
</script>
