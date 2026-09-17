/**
 * Contratos entre modulos de negocio.
 *
 * <p>Este paquete es el unico canal por el que un modulo alcanza a otro. Ningun
 * modulo importa a otro directamente: {@code interaction} no conoce
 * {@code com.codefactory.devnet.project}, conoce
 * {@code IContenidoInteractuable}.</p>
 *
 * <h2>Por que aqui y no en el dominio de cada modulo</h2>
 *
 * <p>ADR-001 planteaba publicar cada puerto en el paquete {@code domain} del modulo
 * destino. Eso se rompe en cuanto dos modulos deben ofrecer la misma capacidad:
 * {@code interaction} necesita comentar tanto proyectos como discusiones, y el
 * puerto habria tenido que declararse dos veces, una en cada dominio, con el mismo
 * nombre y tipos incompatibles.</p>
 *
 * <p>Centralizarlos aqui resuelve eso y ademas simplifica la verificacion: la regla
 * de ArchUnit deja de enumerar pares permitidos y pasa a ser una sola prohibicion,
 * "ningun modulo importa otro modulo".</p>
 *
 * <h2>Reglas</h2>
 * <ul>
 *   <li>Solo interfaces y los {@code record} inmutables que forman su firma.</li>
 *   <li>Nunca entidades JPA. Los tipos que cruzan la frontera son copias, no filas.</li>
 *   <li>Sin dependencias de Spring ni de Jakarta Persistence.</li>
 *   <li>Cada contrato lo implementa la capa {@code infrastructure} o
 *       {@code application} del modulo que lo provee.</li>
 * </ul>
 *
 * <p>Corresponde al paquete {@code integration} de "Compartido" en el diagrama de
 * paquetes, y a los puertos con notacion de paleta del diagrama de componentes.</p>
 */
package com.codefactory.devnet.shared.integration;