# CyMInventory — primera versión

Una app Android para que dos personas planifiquen y registren las compras de su futura casa. La misma información debe aparecer en ambos móviles, con acceso limitado a sus cuentas.

## Objetivo de uso

En pocos segundos, cualquiera de los dos puede responder: qué tenemos, qué nos falta, dónde irá, cuánto costó y dónde está el ticket. Las tareas y la lista de la compra se mantienen compartidas.

## Primera versión funcional

- Acceso de dos personas a un mismo hogar.
- Inventario con estados `por comprar`, `comprado` y `descartado`.
- Ficha del artículo: nombre, categoría, cantidad, tienda, fecha, precio, ubicación prevista, descripción, foto y ticket.
- Lista de la compra y tareas compartidas; cambios visibles en el otro móvil.
- Notas compartidas como entradas independientes, con autor y fecha.
- Resumen de gastos por categoría y periodo a partir de los artículos comprados.
- Búsqueda y filtros por estado, categoría y ubicación.

## Decisiones de producto

- Un artículo pasa de `por comprar` a `comprado`; no se crea una segunda ficha que duplique el gasto.
- El precio se guarda en céntimos para evitar errores de redondeo.
- Las fotos y tickets se vinculan al artículo, con límites de tamaño y compresión de imágenes.
- Las notas son entradas compartidas; la edición simultánea del mismo texto se evaluará más adelante.
- La interfaz se diseñará con referencias visuales elegidas por los usuarios y se revisará en un móvil real antes de extenderla al resto de pantallas.

## Orden de construcción

1. Proyecto Android nativo y navegación básica, instalable en un móvil.
2. Inventario y lista de compra con datos locales de prueba; validar el flujo y el diseño.
3. Cuentas, reglas de acceso y sincronización para los dos móviles.
4. Fotos, tickets, tareas, notas y resumen de gastos.
5. Exportación de datos, pruebas de uso y distribución privada de versiones.

## Tecnología propuesta

Kotlin, Jetpack Compose y Android Studio. Firebase Authentication y Cloud Firestore para cuentas y sincronización; Cloud Storage for Firebase para archivos. El proyecto de Storage requiere activar Blaze (pago por uso). Se establecerán alertas de presupuesto antes de subir archivos reales.
