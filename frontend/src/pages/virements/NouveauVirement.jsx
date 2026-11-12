import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useBlocker, useLocation, useNavigate } from 'react-router-dom'
import { Alert, App, Button, Card, Collapse, Descriptions, Input, Select, Skeleton, Space, Table, Tag, Typography } from 'antd'
import api from '../../api/client'
import ActionButton from '../../components/ActionButton.jsx'
import PageHeader from '../../components/PageHeader.jsx'
import ParcoursEtapes from '../../components/ParcoursEtapes.jsx'
import XsdAnalyse from '../../components/XsdAnalyse.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import { loadingStore } from '../../loading/loadingStore.js'
import { formatMontant, masquerIban } from '../../utils/format.js'
import { dateExecutionDefaut, lirePreparation, messageEchec, sauverPreparation, urlResultat } from './parcours.js'
import '../virements-masse.css'

// Le traitement est un seul appel serveur : les étapes affichées suivent l'ordre réel des contrôles.
const ETAPES_CHARGEMENT = ['Contrôles métier…', 'Analyse IA…', 'Génération du XML…', 'Validation XSD…']
const PAS_ETAPE_MS = 1500

const ROLES_SOCIETES = ['ADMINISTRATEUR', 'GESTIONNAIRE_RH']

const formaterIban = (iban) => iban.replace(/\s+/g, '').replace(/(.{4})/g, '$1 ').trim()

export default function NouveauVirement() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { state } = useLocation()
  const { modal, message } = App.useApp()

  const [xsdList, setXsdList] = useState([])
  const [xsdId, setXsdId] = useState(null)
  const [analyse, setAnalyse] = useState(null)
  const [referentiel, setReferentiel] = useState([])
  const [societes, setSocietes] = useState([])
  const [periodes, setPeriodes] = useState([])
  const [clientId, setClientId] = useState(null)
  const [periode, setPeriode] = useState(null)
  const [dateExecution, setDateExecution] = useState(dateExecutionDefaut())
  // undefined = chargement en cours ; null = aucune société choisie
  const [coordonnees, setCoordonnees] = useState(null)
  const [ibanVisible, setIbanVisible] = useState(false)
  const [paiements, setPaiements] = useState([])
  const [selection, setSelection] = useState([])
  const [filtre, setFiltre] = useState('')
  const [chargementListe, setChargementListe] = useState(false)
  const [erreurListe, setErreurListe] = useState('')
  const sortieAutorisee = useRef(false)
  const numeroRequete = useRef(0)

  const chargerPaiements = async (cid, per, selectionInitiale) => {
    const numero = ++numeroRequete.current
    setChargementListe(true)
    setErreurListe('')
    try {
      const { data } = await api.get('/masse/paiements', { params: { clientId: cid, periode: per }, silent: true })
      if (numero !== numeroRequete.current) return
      setPaiements(data.paiements)
      // Par défaut, les paiements en erreur de contrôle ne sont pas cochés.
      setSelection(selectionInitiale
        ? data.paiements.filter((p) => selectionInitiale.includes(p.id)).map((p) => p.id)
        : data.paiements.filter((p) => !p.enErreur).map((p) => p.id))
    } catch (e) {
      if (numero !== numeroRequete.current) return
      setPaiements([])
      setSelection([])
      setErreurListe(messageEchec(e))
    } finally {
      if (numero === numeroRequete.current) setChargementListe(false)
    }
  }

  useEffect(() => {
    const restauree = state?.restaurer ? lirePreparation() : null
    api.get('/masse/xsd', { silent: true }).then(({ data }) => {
      setXsdList(data)
      const compatibles = data.filter((x) => x.compatible)
      setXsdId(restauree?.xsdId ?? (compatibles.length === 1 ? compatibles[0].id : null))
    }).catch((e) => message.error(`Erreur : ${messageEchec(e)}`))
    api.get('/societes', { silent: true }).then(({ data }) => setReferentiel(data)).catch(() => setReferentiel([]))
    api.get('/masse/filtres', { params: restauree?.clientId ? { clientId: restauree.clientId } : {}, silent: true })
      .then(({ data }) => { setSocietes(data.societes); setPeriodes(data.periodes) })
      .catch((e) => message.error(`Erreur : ${messageEchec(e)}`))
    if (restauree?.clientId && restauree?.periode) {
      setClientId(restauree.clientId)
      setPeriode(restauree.periode)
      if (restauree.dateExecution) setDateExecution(restauree.dateExecution)
      chargerPaiements(restauree.clientId, restauree.periode, restauree.selection)
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!clientId) {
      setCoordonnees(null)
      return undefined
    }
    let actif = true
    setCoordonnees(undefined)
    api.get(`/societes/${encodeURIComponent(clientId)}/coordonnees`, { silent: true })
      .then(({ data }) => actif && setCoordonnees(data))
      .catch((e) => actif && setCoordonnees({ complet: false, manquants: [`coordonnées indisponibles (${messageEchec(e)})`] }))
    return () => { actif = false }
  }, [clientId])

  // Confirmation avant de quitter la page avec une sélection en cours.
  const bloqueur = useBlocker(({ currentLocation, nextLocation }) =>
    selection.length > 0 && !sortieAutorisee.current && currentLocation.pathname !== nextLocation.pathname)

  useEffect(() => {
    if (bloqueur.state !== 'blocked') return
    modal.confirm({
      title: 'Quitter la préparation ?',
      content: 'Votre sélection de paiements n’a pas été utilisée et sera perdue si vous quittez cette page.',
      okText: 'Quitter',
      cancelText: 'Rester',
      okButtonProps: { danger: true },
      onOk: () => bloqueur.proceed(),
      onCancel: () => bloqueur.reset(),
    })
  }, [bloqueur.state]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (selection.length === 0) return undefined
    const avertir = (e) => { if (!sortieAutorisee.current) e.preventDefault() }
    window.addEventListener('beforeunload', avertir)
    return () => window.removeEventListener('beforeunload', avertir)
  }, [selection.length])

  const choisirSociete = (code) => {
    numeroRequete.current++
    setClientId(code)
    setPeriode(null)
    setPeriodes([])
    setPaiements([])
    setSelection([])
    setChargementListe(false)
    setErreurListe('')
    setIbanVisible(false)
    api.get('/masse/filtres', { params: { clientId: code }, silent: true })
      .then(({ data }) => setPeriodes(data.periodes))
      .catch((e) => message.error(`Erreur : ${messageEchec(e)}`))
  }

  const choisirPeriode = (valeur) => {
    setPeriode(valeur)
    chargerPaiements(clientId, valeur)
  }

  const choisirXsd = (id) => {
    setXsdId(id)
    setAnalyse(null)
  }

  const ouvrirAnalyse = (ouverts) => {
    if (ouverts.length === 0 || analyse || !xsdId) return
    api.get(`/masse/xsd/${xsdId}/analyse`, { silent: true })
      .then(({ data }) => setAnalyse(data))
      .catch((e) => message.error(`Analyse du schéma impossible : ${messageEchec(e)}`))
  }

  const optionsSocietes = societes.map((code) => {
    const nom = referentiel.find((s) => s.code === code)?.nom
    return { value: code, label: nom ? `${code} — ${nom}` : code }
  })

  const lignes = useMemo(() => {
    const recherche = filtre.trim().toLowerCase()
    return recherche
      ? paiements.filter((p) => `${p.matricule} ${p.nomBeneficiaire || ''}`.toLowerCase().includes(recherche))
      : paiements
  }, [paiements, filtre])

  const ensembleSelection = useMemo(() => new Set(selection), [selection])
  const totalSelection = paiements
    .filter((p) => ensembleSelection.has(p.id))
    .reduce((somme, p) => somme + Number(p.montant || 0), 0)

  const raisonBlocage = !clientId ? 'Sélectionnez une société : une génération concerne une seule société.'
    : coordonnees === undefined ? 'Chargement des coordonnées bancaires de la société…'
      : !coordonnees?.complet ? 'Les coordonnées bancaires de la société sont incomplètes.'
        : !periode ? 'Sélectionnez la période de paie.'
          : !xsdId ? 'Sélectionnez un schéma XSD pain.001.'
            : !dateExecution ? 'Renseignez la date d’exécution.'
              : selection.length === 0 ? 'Sélectionnez au moins un paiement.'
                : null

  const lancer = async () => {
    const corps = {
      xsdSchemaId: Number(xsdId),
      clientId,
      periode,
      matricules: [],
      transactionIds: [...selection].sort((a, b) => a - b),
      nomDebiteur: null,
      ibanDebiteur: null,
      bicDebiteur: null,
      dateExecution,
      statuts: ['APPROUVEE'],
      exclureErreurs: false,
    }
    const minuteries = ETAPES_CHARGEMENT.slice(1)
      .map((libelle, i) => setTimeout(() => loadingStore.definirLibelle(libelle), (i + 1) * PAS_ETAPE_MS))
    try {
      const { data } = await api.post('/masse/controle', corps, { libelleChargement: ETAPES_CHARGEMENT[0] })
      sauverPreparation({ clientId, periode, xsdId, dateExecution, selection })
      sortieAutorisee.current = true
      navigate(urlResultat(data.id))
    } catch (e) {
      throw new Error(messageEchec(e))
    } finally {
      minuteries.forEach(clearTimeout)
    }
  }

  const colonnes = [
    { title: 'Matricule', dataIndex: 'matricule', sorter: (a, b) => String(a.matricule).localeCompare(String(b.matricule)) },
    { title: 'Nom', dataIndex: 'nomBeneficiaire' },
    { title: 'IBAN', dataIndex: 'ibanMasque' },
    { title: 'BIC', dataIndex: 'bic', render: (bic) => bic || '—' },
    {
      title: 'Montant',
      dataIndex: 'montant',
      align: 'right',
      sorter: (a, b) => Number(a.montant) - Number(b.montant),
      render: formatMontant,
    },
    {
      title: 'Contrôle',
      width: 320,
      render: (_, p) => (p.enErreur
        ? <Tag color="red" style={{ whiteSpace: 'normal' }}>{p.erreurControle}</Tag>
        : <Tag color="green">OK</Tag>),
    },
    {
      title: 'IA',
      render: (_, p) => (p.suspectIa
        ? <Tag color="orange" title={p.raisonsIa || ''}>Suspect {p.scoreIa != null ? p.scoreIa.toFixed(2).replace('.', ',') : ''}</Tag>
        : '—'),
    },
  ]

  const societeIncomplete = coordonnees && !coordonnees.complet
  const peutModifierSocietes = ROLES_SOCIETES.includes(user?.role)

  return (
    <div>
      <PageHeader
        title="Nouveau virement de masse"
        subtitle="Choisissez la société, la période et les salariés à payer, puis lancez le contrôle."
        actions={[<Link key="historique" to="/virements-masse/historique"><Button>Historique des fichiers</Button></Link>]}
      />
      <ParcoursEtapes etape={0} />

      <Card title="1. Société débitrice" style={{ marginBottom: 16 }}>
        <Select
          showSearch
          allowClear
          placeholder="Choisir une société"
          style={{ width: 360, maxWidth: '100%' }}
          value={clientId}
          options={optionsSocietes}
          optionFilterProp="label"
          onChange={choisirSociete}
          onClear={() => choisirSociete(null)}
        />
        {coordonnees === undefined && <Skeleton active paragraph={{ rows: 2 }} style={{ marginTop: 16 }} />}
        {coordonnees && (
          <div style={{ marginTop: 16 }}>
            {societeIncomplete && (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 12 }}
                message="Coordonnées bancaires incomplètes : la génération est bloquée."
                description={`À compléter : ${coordonnees.manquants.join(', ')}.`}
              />
            )}
            {coordonnees.nom && (
              <Descriptions bordered size="small" column={1} style={{ maxWidth: 640 }}>
                <Descriptions.Item label="Raison sociale">{coordonnees.nom}</Descriptions.Item>
                <Descriptions.Item label="IBAN">
                  <Space>
                    <span>{ibanVisible ? formaterIban(coordonnees.iban) : masquerIban(coordonnees.iban)}</span>
                    <Button size="small" type="link" onClick={() => setIbanVisible(!ibanVisible)}>
                      {ibanVisible ? 'Masquer' : 'Afficher'}
                    </Button>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="BIC">{coordonnees.bic}</Descriptions.Item>
              </Descriptions>
            )}
            {peutModifierSocietes && (
              <Link to="/societes" style={{ display: 'inline-block', marginTop: 8 }}>Modifier les coordonnées</Link>
            )}
          </div>
        )}
      </Card>

      <Card title="2. Paramètres du fichier" style={{ marginBottom: 16 }}>
        <Space wrap size="large" align="start">
          <label>
            <div>Période de paie</div>
            <Select
              placeholder={clientId ? 'Choisir une période' : "Choisir d'abord une société"}
              style={{ width: 200 }}
              disabled={!clientId}
              value={periode}
              options={periodes.map((p) => ({ value: p, label: p }))}
              onChange={choisirPeriode}
            />
          </label>
          <label>
            <div>Schéma XSD</div>
            <Select
              placeholder="Choisir un schéma"
              style={{ width: 320 }}
              value={xsdId}
              onChange={choisirXsd}
              options={xsdList.map((x) => ({
                value: x.id,
                disabled: !x.compatible,
                label: `${x.nom}${x.versionMessage ? ` (${x.versionMessage})` : ''}${x.compatible ? '' : ' — non compatible'}`,
              }))}
            />
          </label>
          <label>
            <div>Date d’exécution</div>
            <Input type="date" style={{ width: 170 }} value={dateExecution} onChange={(e) => setDateExecution(e.target.value)} />
          </label>
        </Space>
        {xsdId && (
          <Collapse
            ghost
            style={{ marginTop: 12 }}
            onChange={ouvrirAnalyse}
            items={[{ key: 'xsd', label: 'Analyse du schéma XSD', children: <XsdAnalyse analyse={analyse} /> }]}
          />
        )}
      </Card>

      <Card title="3. Salariés à payer" style={{ marginBottom: 16 }}>
        {erreurListe && <Alert type="error" showIcon message={erreurListe} style={{ marginBottom: 12 }} />}
        {!periode && !erreurListe && <Typography.Text type="secondary">Choisissez une société et une période pour afficher les paiements approuvés.</Typography.Text>}
        {(periode || chargementListe) && (
          <>
            <Space wrap style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
              <Typography.Text>
                <strong>{selection.length}</strong> / {paiements.length} paiement(s) sélectionné(s) — Total <strong>{formatMontant(totalSelection)}</strong>
              </Typography.Text>
              <Space wrap>
                <Input.Search
                  allowClear
                  placeholder="Filtrer par matricule ou nom…"
                  style={{ width: 260 }}
                  onChange={(e) => setFiltre(e.target.value)}
                />
                <Button onClick={() => setSelection([...new Set([...selection, ...lignes.map((p) => p.id)])])} disabled={lignes.length === 0}>Tout sélectionner</Button>
                <Button onClick={() => setSelection([])} disabled={selection.length === 0}>Tout désélectionner</Button>
              </Space>
            </Space>
            <Table
              rowKey="id"
              size="small"
              columns={colonnes}
              dataSource={lignes}
              loading={chargementListe}
              rowClassName={(p) => (p.enErreur || p.suspectIa ? 'row-suspect' : '')}
              rowSelection={{
                selectedRowKeys: selection,
                preserveSelectedRowKeys: true,
                onChange: setSelection,
                selections: [Table.SELECTION_ALL, Table.SELECTION_NONE, Table.SELECTION_INVERT],
              }}
              pagination={{ defaultPageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50], showTotal: (n) => `${n} paiement(s)` }}
              scroll={{ x: 'max-content' }}
              locale={{ emptyText: 'Aucun paiement approuvé pour cette société et cette période.' }}
            />
          </>
        )}
      </Card>

      <Space align="center" wrap>
        <ActionButton
          onClick={lancer}
          disabled={!!raisonBlocage}
          succes="Contrôle terminé : le résultat est enregistré."
          confirmation={{
            titre: 'Lancer le contrôle et générer le fichier ?',
            contenu: `${selection.length} paiement(s) pour un total de ${formatMontant(totalSelection)}. Les contrôles métier, l’analyse IA, la génération du XML et la validation XSD seront exécutés.`,
            libelle: 'Lancer',
          }}
        >
          Lancer le contrôle et générer
        </ActionButton>
        {raisonBlocage && <Typography.Text type="secondary">{raisonBlocage}</Typography.Text>}
      </Space>
    </div>
  )
}
