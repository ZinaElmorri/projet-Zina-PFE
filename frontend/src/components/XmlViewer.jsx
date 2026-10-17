import { useEffect, useMemo, useRef } from 'react'

// Colorise une ligne XML en segments (balises, attributs, valeurs, texte).
function segments(line) {
  const out = []
  const regex = /(<\/?)([\w:.-]+)((?:\s+[\w:.-]+="[^"]*")*)\s*(\/?>)|([^<]+)/g
  let m
  while ((m = regex.exec(line)) !== null) {
    if (m[5] !== undefined) {
      out.push({ t: 'text', v: m[5] })
      continue
    }
    out.push({ t: 'punct', v: m[1] })
    out.push({ t: 'tag', v: m[2] })
    if (m[3]) {
      const attrRe = /\s+([\w:.-]+)="([^"]*)"/g
      let a
      while ((a = attrRe.exec(m[3])) !== null) {
        out.push({ t: 'text', v: ' ' })
        out.push({ t: 'attr', v: a[1] })
        out.push({ t: 'punct', v: '="' })
        out.push({ t: 'val', v: a[2] })
        out.push({ t: 'punct', v: '"' })
      }
    }
    out.push({ t: 'punct', v: m[4] })
  }
  return out
}

export default function XmlViewer({ xml, erreurs = [], avertissements = [], ligneActive }) {
  const lignesEnErreur = useMemo(
    () => new Set((erreurs || []).map((e) => e.ligne).filter((n) => n > 0)),
    [erreurs]
  )
  const lines = useMemo(() => (xml ? xml.replace(/\r\n/g, '\n').split('\n') : []), [xml])
  const lignesEnAvertissement = useMemo(
    () => new Set((avertissements || []).map((avertissement) => avertissement.ligne).filter((numero) => numero > 0)),
    [avertissements]
  )
  const conteneur = useRef(null)

  useEffect(() => {
    if (ligneActive && conteneur.current) {
      const el = conteneur.current.querySelector(`[data-ligne="${ligneActive}"]`)
      if (el) el.scrollIntoView({ block: 'center', behavior: 'smooth' })
    }
  }, [ligneActive])

  if (!xml) return null

  return (
    <div className="xml-viewer" ref={conteneur}>
      {lines.map((line, i) => {
        const n = i + 1
        const cls = `xml-line${lignesEnErreur.has(n) ? ' xml-line--err' : lignesEnAvertissement.has(n) ? ' xml-line--warn' : ''}${ligneActive === n ? ' xml-line--active' : ''}`
        return (
          <div className={cls} data-ligne={n} key={n}>
            <span className="xml-gutter">{n}</span>
            <span className="xml-code">
              {segments(line).map((s, j) => (
                <span className={`xml-${s.t}`} key={j}>{s.v}</span>
              ))}
            </span>
          </div>
        )
      })}
    </div>
  )
}
