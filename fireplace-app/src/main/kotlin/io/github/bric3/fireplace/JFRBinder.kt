/*
 * Fireplace
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.fireplace

import org.openjdk.jmc.common.item.IItem
import org.openjdk.jmc.common.item.IItemCollection
import org.openjdk.jmc.common.item.IItemIterable
import org.openjdk.jmc.common.item.IType
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors.toUnmodifiableList
import javax.swing.*

internal class JFRBinder {
    private val eventsBinders: MutableList<Consumer<IItemCollection>> = mutableListOf()
    private val pathsBinders: MutableList<Consumer<List<Path>>> = mutableListOf()
    private var onLoadStart: Runnable? = null
    private var onLoadEnd: Runnable? = null
    fun bindPaths(pathsBinder: Consumer<List<Path>>) {
        pathsBinders.add { paths -> SwingUtilities.invokeLater { pathsBinder.accept(paths) } }
    }

    fun <T> bindEvents(provider: Function<IItemCollection, T>, componentUpdate: Consumer<T>) {
        eventsBinders.add { events: IItemCollection ->
            CompletableFuture.supplyAsync { provider.apply(events) }
                .whenComplete { result, throwable ->
                    if (throwable != null) {
                        throwable.printStackTrace()
                    } else {
                        SwingUtilities.invokeLater { componentUpdate.accept(result) }
                    }
                }
        }
    }

    fun load(jfrPaths: List<Path>) {
        if (jfrPaths.isEmpty()) {
            return
        }
        onLoadStart!!.run()
        CompletableFuture.runAsync {
            pathsBinders.forEach { it.accept(jfrPaths) }
            
            val jfrFiles = jfrPaths.stream()
                .peek { path -> println("Loading $path") }
                .map { path -> path.toFile() }
                .collect(toUnmodifiableList())
            
            val eventSupplier = Utils.memoize {
                CompletableFuture.supplyAsync {
                    val events: IItemCollection = try {
                        JfrLoaderToolkit.loadEvents(jfrFiles)
                    } catch (ioe: IOException) {
                        throw UncheckedIOException(ioe)
                    } catch (e1: CouldNotLoadRecordingException) {
                        throw RuntimeException(e1)
                    }
                    events.stream()
                        .flatMap(IItemIterable::stream)
                        .map(IItem::getType)
                        .map(IType<*>::getIdentifier)
                        .distinct()
                        .forEach { println(it) }
                    events
                }.join()
            }
            eventsBinders.forEach { binder -> binder.accept(eventSupplier.get()) }
            onLoadEnd!!.run()
        }
    }

    fun setOnLoadActions(onLoadStart: Runnable?, onLoadEnd: Runnable?) {
        this.onLoadStart = Runnable { SwingUtilities.invokeLater(onLoadStart) }
        this.onLoadEnd = Runnable { SwingUtilities.invokeLater(onLoadEnd) }
    }
}