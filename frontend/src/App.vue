<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { Toaster } from '@/components/ui/sonner'
import AppNav from '@/components/layout/AppNav.vue'
import ChangePasswordDialog from '@/components/business/ChangePasswordDialog.vue'

const route = useRoute()
const showNav = computed(() => route.path !== '/login')
const pwdOpen = ref(false)
</script>

<template>
  <div class="min-h-screen">
    <AppNav v-if="showNav" @change-password="pwdOpen = true" />
    <RouterView />
  </div>
  <ChangePasswordDialog v-model:open="pwdOpen" />
  <Toaster position="bottom-center" rich-colors close-button />
</template>
