// I fogli (sheet) leggono le proprietà una volta sola, all'apertura: servono i valori di partenza, non il seguito.
export default {
	compilerOptions: { warningFilter: (w) => w.code !== 'state_referenced_locally' }
}
