/**
 * 
 */
package com.test;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

/**
 * @author vinod
 *
 */
public class ExternalDirWatchService {
	
	private static final String WATCH_DIR = "/Users/vinod/Desktop/external-dir-watch/inbound/";
	private static final String BACKUP_DIR = "/Users/vinod/Desktop/external-dir-watch/backup/";

	private final WatchService watchService;
	private final Map<WatchKey, Path> keys;
	private final boolean recursive;
	private boolean trace = true;

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	/**
	 * Register the given directory with the WatchService
	 */
	private void register(Path dir) throws IOException {
		WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
		if (trace) {
			Path prev = keys.get(key);
			if (prev == null) {
				System.out.format("register: %s\n", dir);
			} else {
				if (!dir.equals(prev)) {
					System.out.format("update: %s -> %s\n", prev, dir);
				}
			}
		}
		keys.put(key, dir);
	}

	/**
	 * Register the given directory, and all its sub-directories, with the
	 * WatchService.
	 */
	private void registerAll(final Path start) throws IOException {
		// register directory and sub-directories
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				register(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Creates a WatchService and registers the given directory
	 */
	ExternalDirWatchService(Path dir, boolean recursive) throws IOException {
		this.watchService = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<WatchKey, Path>();
		this.recursive = recursive;

		if (recursive) {
			System.out.format("Scanning %s ...\n", dir);
			registerAll(dir);
			System.out.println("Done.");
		} else {
			register(dir);
		}

		// enable trace after initial registration
		this.trace = true;
	}

	/**
	 * Process all events for keys queued to the watcher
	 */
	void processEvents() {
		for (;;) {

			// wait for key to be signalled
			WatchKey key;
			try {
				key = watchService.take();
			} catch (InterruptedException x) {
				return;
			}

			Path dir = keys.get(key);
			if (dir == null) {
				System.err.println("WatchKey not recognized!!");
				continue;
			}

			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind kind = event.kind();

				// TBD - provide example of how OVERFLOW event is handled
				if (kind == OVERFLOW) {
					continue;
				}

				// Context for directory entry event is the file name of entry
				WatchEvent<Path> ev = cast(event);
				Path name = ev.context();
				Path child = dir.resolve(name);

				// print out event
				System.out.format("%s: %s\n", event.kind().name(), child);

				// if directory is created, and watching recursively, then
				// register it and its sub-directories
				if (recursive && (kind == ENTRY_CREATE)) {
					try {
						if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
							System.out.println("New Dir register");
							registerAll(child);
						} else {
							String inboundDirs = child.toString().substring(child.toString().indexOf("inbound")+8, child.toString().indexOf(name.toString()));
							Path backupDir = Paths.get(BACKUP_DIR+inboundDirs);
							Files.createDirectories(backupDir);
							
							backupDir = backupDir.resolve(name.toString());
							
							Files.move(child, backupDir, StandardCopyOption.REPLACE_EXISTING);
						}
					} catch (IOException x) {
						// ignore to keep sample readbale
					}
				}
			}

			// reset key and remove from set if directory no longer accessible
			boolean valid = key.reset();
			if (!valid) {
				keys.remove(key);

				// all directories are inaccessible
				/*if (keys.isEmpty()) {
					break;
				}*/
			}
		}
	}

	static void usage() {
		System.err.println("usage: java ExternalDirWatchService [-r] dir");
		System.exit(-1);
	}

	public static void main(String[] args) throws IOException {
		// parse arguments
		/*if (args.length == 0 || args.length > 2)
			usage();
		boolean recursive = false;
		int dirArg = 0;
		if (args[0].equals("-r")) {
			if (args.length < 2)
				usage();
			recursive = true;
			dirArg++;
		}*/

		// register directory and process its events
		//Path dir = Paths.get(args[dirArg]);
		Path dir = Paths.get(WATCH_DIR);
		new ExternalDirWatchService(dir, true).processEvents();
	}
}
