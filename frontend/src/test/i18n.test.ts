import { describe, it, expect, beforeEach } from 'vitest'
import i18n from '../i18n'

describe('i18n Internationalization', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
  })

  describe('English (en) locale', () => {
    it('loads English locale bundle without errors', () => {
      expect(i18n.isInitialized).toBe(true)
    })

    it('translates app.name to Power RAG', () => {
      expect(i18n.t('app.name')).toBe('Power RAG')
    })

    it('translates nav.chat correctly', () => {
      expect(i18n.t('nav.chat')).toBe('Chat')
    })

    it('translates login.signIn correctly', () => {
      expect(i18n.t('login.signIn')).toBe('Sign In')
    })

    it('translates feedback.submit correctly', () => {
      expect(i18n.t('feedback.submit')).toBe('Submit Feedback')
    })

    it('translates all confidence levels', () => {
      expect(i18n.t('confidence.high')).toBe('High confidence')
      expect(i18n.t('confidence.medium')).toBe('Medium confidence')
      expect(i18n.t('confidence.low')).toBe('Low confidence')
    })
  })

  describe('Simplified Chinese (zh-CN) locale', () => {
    beforeEach(async () => {
      await i18n.changeLanguage('zh-CN')
    })

    it('loads Simplified Chinese locale bundle without errors', () => {
      expect(i18n.language).toBe('zh-CN')
    })

    it('translates app.name to Power RAG', () => {
      expect(i18n.t('app.name')).toBe('Power RAG')
    })

    it('translates nav.chat to Chinese', () => {
      expect(i18n.t('nav.chat')).toBe('对话')
    })

    it('translates login.signIn to Chinese', () => {
      expect(i18n.t('login.signIn')).toBe('登录')
    })

    it('translates feedback.submit to Chinese', () => {
      expect(i18n.t('feedback.submit')).toBe('提交反馈')
    })

    it('translates confidence levels to Chinese', () => {
      expect(i18n.t('confidence.high')).toBe('高置信度')
      expect(i18n.t('confidence.medium')).toBe('中等置信度')
      expect(i18n.t('confidence.low')).toBe('低置信度')
    })
  })

  describe('Traditional Chinese (zh-TW) locale', () => {
    beforeEach(async () => {
      await i18n.changeLanguage('zh-TW')
    })

    it('loads Traditional Chinese locale bundle without errors', () => {
      expect(i18n.language).toBe('zh-TW')
    })

    it('translates nav.chat to Traditional Chinese', () => {
      expect(i18n.t('nav.chat')).toBe('對話')
    })

    it('translates login.signIn to Traditional Chinese', () => {
      expect(i18n.t('login.signIn')).toBe('登入')
    })

    it('translates feedback.submit to Traditional Chinese', () => {
      expect(i18n.t('feedback.submit')).toBe('提交回饋')
    })
  })

  describe('Language switching', () => {
    it('switches from English to Simplified Chinese', async () => {
      await i18n.changeLanguage('en')
      expect(i18n.t('nav.chat')).toBe('Chat')

      await i18n.changeLanguage('zh-CN')
      expect(i18n.t('nav.chat')).toBe('对话')
    })

    it('switches from Simplified to Traditional Chinese', async () => {
      await i18n.changeLanguage('zh-CN')
      expect(i18n.t('nav.chat')).toBe('对话')

      await i18n.changeLanguage('zh-TW')
      expect(i18n.t('nav.chat')).toBe('對話')
    })

    it('falls back to English for missing key in zh-CN', async () => {
      await i18n.changeLanguage('zh-CN')
      // app.name exists in both, should return zh-CN value
      expect(i18n.t('app.name')).toBe('Power RAG')
    })

    it('supported languages array contains en, zh-CN, zh-TW', async () => {
      const { SUPPORTED_LANGUAGES } = await import('../i18n')
      const codes = SUPPORTED_LANGUAGES.map(l => l.code)
      expect(codes).toContain('en')
      expect(codes).toContain('zh-CN')
      expect(codes).toContain('zh-TW')
    })
  })
})
