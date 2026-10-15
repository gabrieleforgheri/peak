import { test } from 'node:test'
import assert from 'node:assert/strict'
import { addItem, blankItem, differsFromTemplate, itemFromSets, moveItem, removeItem, replaceItem, sessionItems } from './plan.ts'

const item = (exercise: string, sets = 4) => ({ exercise, sets, repsMin: 8, repsMax: 10, weight: 60 })
const set = (exercise: string | null, weight: number, reps: number, isWarmup = false) => ({ exercise, weight, reps, isWarmup })
const all = () => true

test('itemFromSets: serie di lavoro, rip min–max, peso massimo; il riscaldamento non conta', () => {
  const sets = [set('a', 20, 12, true), set('a', 60, 10), set('a', 70, 8), set('b', 5, 5)]
  assert.deepEqual(itemFromSets('a', sets), { exercise: 'a', sets: 2, repsMin: 8, repsMax: 10, weight: 70 })
  assert.deepEqual(itemFromSets('c', sets), blankItem('c'))
})

test('sessionItems: piano, poi gli esercizi fuori piano; i macchinari spariti si saltano', () => {
  const plan = [item('a'), item('gone'), item('b')]
  const sets = [set('c', 30, 10), set('a', 60, 8)]
  const ex = (id: string) => id !== 'gone'
  assert.deepEqual(sessionItems(plan, sets, ex).map((i) => i.exercise), ['a', 'b', 'c'])
  assert.deepEqual(sessionItems(plan, sets, ex)[2], blankItem('c'))
  assert.deepEqual(sessionItems(plan, sets, ex, 'targets')[2], itemFromSets('c', sets))
  assert.deepEqual(sessionItems(plan, sets, ex, 'targets')[0], item('a'), 'gli obiettivi scritti restano')
  assert.deepEqual(sessionItems(plan, sets, ex, 'done')[0], itemFromSets('a', sets), 'fatto vince sull\'obiettivo')
  assert.deepEqual(sessionItems(plan, sets, ex, 'done')[1], item('b'), 'senza serie resta l\'obiettivo')
})

test('aggiungi, togli, sostituisci, sposta', () => {
  const l = [item('a'), item('b'), item('c')]
  assert.deepEqual(addItem(l, 'd').map((i) => i.exercise), ['a', 'b', 'c', 'd'])
  assert.equal(addItem(l, 'a'), l, 'già in lista: nulla')
  assert.deepEqual(removeItem(l, 'b').map((i) => i.exercise), ['a', 'c'])
  const r = replaceItem(l, 'b', 'x')
  assert.deepEqual(r[1], { exercise: 'x', sets: 4, repsMin: 8, repsMax: 10, weight: null })
  assert.equal(replaceItem(l, 'b', 'c'), l, 'l\'altro è già in lista: nulla')
  assert.deepEqual(moveItem(l, 'c', -1).map((i) => i.exercise), ['a', 'c', 'b'])
  assert.equal(moveItem(l, 'a', -1), l)
  assert.equal(moveItem(l, 'c', 1), l)
})

test('differsFromTemplate: conta esercizi e ordine, non gli obiettivi', () => {
  const tpl = [item('a'), item('b'), item('gone')]
  const ex = (id: string) => id !== 'gone'
  assert.equal(differsFromTemplate([item('a', 1), item('b', 9)], tpl, ex), false)
  assert.equal(differsFromTemplate([item('b'), item('a')], tpl, ex), true)
  assert.equal(differsFromTemplate([item('a')], tpl, ex), true)
  assert.equal(differsFromTemplate([item('a'), item('b'), item('c')], tpl, all), true)
})
