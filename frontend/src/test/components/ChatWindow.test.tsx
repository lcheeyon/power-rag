import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import '../../i18n'
import ChatWindow from '../../components/ChatWindow'
import { MODEL_OPTIONS } from '../../components/ModelSelector'

const model = MODEL_OPTIONS[0]

function renderChat() {
  return render(<ChatWindow model={model} language="en" />)
}

describe('ChatWindow', () => {
  it('renders the chat window', () => {
    renderChat()
    expect(screen.getByTestId('chat-window')).toBeInTheDocument()
  })

  it('renders the message list', () => {
    renderChat()
    expect(screen.getByTestId('chat-messages')).toBeInTheDocument()
  })

  it('renders the input area', () => {
    renderChat()
    expect(screen.getByTestId('chat-input-area')).toBeInTheDocument()
  })

  it('renders the text input', () => {
    renderChat()
    expect(screen.getByTestId('chat-input')).toBeInTheDocument()
  })

  it('renders the send button', () => {
    renderChat()
    expect(screen.getByTestId('chat-send-button')).toBeInTheDocument()
  })

  it('send button is disabled when input is empty', () => {
    renderChat()
    const btn = screen.getByTestId('chat-send-button') as HTMLButtonElement
    expect(btn.disabled).toBe(true)
  })

  it('send button is enabled after typing', () => {
    renderChat()
    const input = screen.getByTestId('chat-input')
    fireEvent.change(input, { target: { value: 'What is RAG?' } })
    const btn = screen.getByTestId('chat-send-button') as HTMLButtonElement
    expect(btn.disabled).toBe(false)
  })

  it('sends a message and shows AI response', async () => {
    renderChat()
    const input = screen.getByTestId('chat-input')
    fireEvent.change(input, { target: { value: 'What is RAG?' } })
    fireEvent.click(screen.getByTestId('chat-send-button'))

    // User message appears
    await waitFor(() =>
      expect(screen.getByTestId('message-user')).toBeInTheDocument(),
    )

    // AI response appears
    await waitFor(() =>
      expect(screen.getByTestId('message-assistant')).toBeInTheDocument(),
    )
  })

  it('clears input after sending', async () => {
    renderChat()
    const input = screen.getByTestId('chat-input') as HTMLTextAreaElement
    fireEvent.change(input, { target: { value: 'Hello' } })
    fireEvent.click(screen.getByTestId('chat-send-button'))
    await waitFor(() => expect(input.value).toBe(''))
  })

  it('shows confidence badge after response', async () => {
    renderChat()
    const input = screen.getByTestId('chat-input')
    fireEvent.change(input, { target: { value: 'What is RAG?' } })
    fireEvent.click(screen.getByTestId('chat-send-button'))
    await waitFor(() =>
      expect(screen.getByTestId('confidence-badge')).toBeInTheDocument(),
    )
  })
})
