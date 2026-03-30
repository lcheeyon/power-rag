import type { ReactNode } from 'react'

const linkClass =
  'text-indigo-400 hover:text-indigo-300 underline underline-offset-2 font-medium'

/**
 * Jira Cloud-style keys: project (2–15 alnum starting with letter) + hyphen + digits.
 * No \\b after the number so "KAN-5KAN-4" still yields two matches (abutted keys).
 */
const JIRA_KEY = /([A-Z][A-Z0-9]{1,14}-\d+)/g

const linkSepClass = 'text-slate-500 select-none'

/**
 * Turns bare issue keys (e.g. KAN-5) into links to Jira browse URLs.
 * Abutted keys (KAN-5KAN-4) become separate links with a visible separator.
 * Safe to call with empty baseUrl — returns plain text only.
 */
export function renderAssistantTextWithJiraIssueLinks(
  text: string,
  browseBaseUrl: string,
): ReactNode {
  if (!text) return text
  const base = browseBaseUrl.replace(/\/$/, '')
  if (!base) return text

  const out: ReactNode[] = []
  let last = 0
  let m: RegExpExecArray | null
  let seq = 0
  const re = new RegExp(JIRA_KEY.source, 'g')
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) {
      out.push(<span key={`t${seq++}`}>{text.slice(last, m.index)}</span>)
    } else if (m.index === last && last > 0) {
      out.push(
        <span key={`sep${seq++}`} className={linkSepClass} aria-hidden>
          {' · '}
        </span>,
      )
    }
    const issueKey = m[1]
    const href = `${base}/browse/${encodeURIComponent(issueKey)}`
    const summaryFromContext = extractSummaryAfterKey(text, m.index + issueKey.length)
    out.push(
      <a
        key={`j${seq++}`}
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        className={linkClass}
        title={summaryFromContext ? `${issueKey}: ${summaryFromContext}` : href}
      >
        {issueKey}
      </a>,
    )
    if (summaryFromContext) {
      out.push(
        <span key={`sum${seq++}`} className="text-slate-300">
          {' — '}
          {summaryFromContext}
        </span>,
      )
    }
    last = re.lastIndex
  }
  if (last < text.length) {
    out.push(<span key={`t${seq++}`}>{text.slice(last)}</span>)
  }
  return out.length > 0 ? <>{out}</> : text
}

/**
 * If the model wrote "KAN-5 — Fix bug" or "**KAN-5** — Fix bug" on the same line, show the title after the link.
 * Stops before another issue key on the same line.
 */
function extractSummaryAfterKey(fullText: string, afterKeyIndex: number): string | null {
  const tail = fullText.slice(afterKeyIndex)
  const firstLine = tail.split(/\r?\n/, 1)[0] ?? ''
  const cleaned = firstLine.replace(/^\s*\*+/, '').trimStart()
  const m = cleaned.match(
    /^\s*[—:–]\s*(.+?)(?=\s+[A-Z][A-Z0-9]{1,14}-\d+|$)/,
  )
  if (!m) return null
  let s = m[1].replace(/\*+/g, '').trim()
  if (s.length < 2 || s.length > 220) return null
  return s
}
