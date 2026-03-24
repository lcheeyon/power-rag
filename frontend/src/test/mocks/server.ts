import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/**
 * MSW server instance used in Vitest (Node environment).
 * Started/stopped in test/setup.ts.
 */
export const server = setupServer(...handlers)
