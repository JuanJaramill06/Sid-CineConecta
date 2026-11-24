// =============================================================================
// CINECONECTA - MongoDB: catálogo de objetos (colecciones, validadores, índices)
//
//   mongosh "$env:MONGO_URI" --file database/mongodb/08_catalog.js
//
// Primero un resumen por colección y después el $jsonSchema completo de cada una.
// La salida se copia a docs/catalogo-objetos.md.
// =============================================================================
db = db.getSiblingDB('cineconecta');

const infos = db.getCollectionInfos()
  .filter(i => !i.name.startsWith('system.'))
  .sort((a, b) => a.name.localeCompare(b.name));

print(`Base de datos: ${db.getName()} — ${infos.length} colecciones\n`);

for (const info of infos) {
  const coll = db.getCollection(info.name);
  const schema = info.options?.validator?.$jsonSchema;
  print(`## ${info.name} (${info.type}) — ${coll.estimatedDocumentCount()} documentos`);
  if (schema) {
    print(`  Validación: level=${info.options.validationLevel ?? 'strict'}, action=${info.options.validationAction ?? 'error'}`);
    print(`  Obligatorios: ${(schema.required || []).join(', ')}`);
    print(`  Propiedades:  ${Object.keys(schema.properties || {}).join(', ')}`);
    if (schema.oneOf) print(`  oneOf: ${schema.oneOf.length} variantes`);
  } else {
    print('  Sin validador');
  }
  print('  Índices:');
  for (const ix of coll.getIndexes()) {
    const extras = [
      ix.unique && 'único',
      ix.partialFilterExpression && `parcial ${JSON.stringify(ix.partialFilterExpression)}`,
      ix.default_language && `idioma ${ix.default_language}`,
      ix.weights && `texto sobre ${Object.keys(ix.weights).join(', ')}`,
    ].filter(Boolean).join('; ');
    print(`    ${ix.name.padEnd(30)} ${JSON.stringify(ix.key)}${extras ? '  (' + extras + ')' : ''}`);
  }
  print('');
}

print('==================== $jsonSchema completo ====================');
for (const info of infos) {
  print(`\n## ${info.name}`);
  printjson(info.options?.validator ?? {});
}
