import { test, expect } from '@playwright/test'

/**
 * Phase 1 Smoke Tests – verify the app loads and core pages render correctly.
 * These tests run against the live dev server (localhost:3000).
 */

test.describe('Phase 1 Smoke Tests', () => {

  test('app loads and redirects to /chat', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/chat/)
    await expect(page.getByTestId('app-root')).toBeVisible()
  })

  test('chat page renders three-panel layout', async ({ page }) => {
    await page.goto('/chat')
    await expect(page.getByTestId('chat-page')).toBeVisible()
    await expect(page.getByTestId('knowledge-base-sidebar')).toBeVisible()
    await expect(page.getByTestId('chat-main')).toBeVisible()
    await expect(page.getByTestId('sources-sidebar')).toBeVisible()
  })

  test('Power RAG branding is visible on chat page', async ({ page }) => {
    await page.goto('/chat')
    await expect(page.getByText('Power RAG')).toBeVisible()
  })

  test('login page renders correctly', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByTestId('login-page')).toBeVisible()
    await expect(page.getByTestId('login-form')).toBeVisible()
    await expect(page.getByTestId('login-username-input')).toBeVisible()
    await expect(page.getByTestId('login-password-input')).toBeVisible()
    await expect(page.getByTestId('login-submit-button')).toBeVisible()
  })

  test('login page has correct title', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByText('Power RAG')).toBeVisible()
  })

  test('admin page renders', async ({ page }) => {
    await page.goto('/admin')
    await expect(page.getByTestId('admin-page')).toBeVisible()
  })

  test('unknown route redirects to /chat', async ({ page }) => {
    await page.goto('/unknown-route-xyz')
    await expect(page).toHaveURL(/\/chat/)
  })

  test('page title is Power RAG', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/Power RAG/)
  })

  test('login form accepts username and password input', async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-username-input').fill('admin')
    await page.getByTestId('login-password-input').fill('Admin@1234')
    await expect(page.getByTestId('login-username-input')).toHaveValue('admin')
    await expect(page.getByTestId('login-password-input')).toHaveValue('Admin@1234')
  })

  test('page background matches dark design system', async ({ page }) => {
    await page.goto('/chat')
    const body = page.locator('body')
    const bgColor = await body.evaluate(el =>
      window.getComputedStyle(el).backgroundColor
    )
    // Should be a very dark color (near #0A0A0F = rgb(10, 10, 15))
    expect(bgColor).not.toBe('rgb(255, 255, 255)')
  })
})
