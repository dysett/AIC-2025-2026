using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

internal static class Program
{
    private const string DriverJar = "sqlite-jdbc-3.53.1.0.jar";

    [STAThread]
    private static int Main()
    {
        try
        {
            // AppContext.BaseDirectory вказує на папку, де лежить ZLAGODA AIS.exe.
            // Саме цю папку вважаємо коренем проєкту, бо поруч із exe лежать src, lib і data.
            string root = AppContext.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);

            // Робоча папка потрібна SQLite-драйверу, бо Java-код відкриває базу за відносним шляхом data/zlagoda.db.
            // Якщо не перейти в root, база може створитися не в папці проєкту.
            Directory.SetCurrentDirectory(root);

            string srcDir = Path.Combine(root, "src");
            string libDir = Path.Combine(root, "lib");
            string jarPath = Path.Combine(libDir, DriverJar);

            if (!Directory.Exists(srcDir))
            {
                // Без src немає Java-класів застосунку, тому запуск не має сенсу.
                throw new InvalidOperationException("Не знайдено папку src поряд із ZLAGODA AIS.exe.");
            }

            if (!File.Exists(jarPath))
            {
                // SQLite JDBC потрібен Java-програмі для підключення до локальної бази.
                throw new InvalidOperationException("Не знайдено драйвер SQLite JDBC у папці lib.");
            }

            string mainClass = Path.Combine(srcDir, "zlagoda", "Main.class");
            if (!File.Exists(mainClass))
            {
                // Якщо програма ще не компілювалася, лаунчер один раз запускає javac.
                // Після появи Main.class наступні старти йдуть швидше, без повторної компіляції.
                CompileJavaSources(root, srcDir);
            }

            // Після перевірок запускається Java Swing-застосунок.
            // Лаунчер завершує свою роботу, а Java-вікно живе окремим процесом.
            StartSwingApplication(root);
            return 0;
        }
        catch (Exception ex)
        {
            // Для GUI-лаунчера помилка показується через MessageBox,
            // бо консольне вікно навмисно не відкривається.
            ShowError(ex.Message);
            return 1;
        }
    }

    private static void CompileJavaSources(string root, string srcDir)
    {
        string[] sources = Directory.GetFiles(srcDir, "*.java", SearchOption.AllDirectories);
        if (sources.Length == 0)
        {
            // Якщо немає жодного .java, це означає неповний комплект файлів проєкту.
            throw new InvalidOperationException("У папці src немає Java-файлів для компіляції.");
        }

        Directory.CreateDirectory(Path.Combine(root, "data"));
        string argsFile = Path.Combine(root, "data", "javac.args");

        // Аргументи зберігаються відносними шляхами, щоб запуск працював і в папці з українською назвою.
        // Окремий файл аргументів також обходить проблему дуже довгого командного рядка,
        // коли Java-файлів у src багато.
        List<string> args = new()
        {
            "-encoding",
            "UTF-8",
            "-cp",
            Path.Combine("lib", DriverJar)
        };
        args.AddRange(sources.Select(path => Path.GetRelativePath(root, path)));

        // UTF8Encoding(false) записує файл без BOM.
        // javac сприймає BOM на початку arg-файлу як частину першого параметра, тому BOM тут не можна додавати.
        File.WriteAllLines(argsFile, args, new UTF8Encoding(false));

        try
        {
            // waitForExit: true означає, що лаунчер чекає завершення javac і читає текст помилки.
            ProcessResult result = RunHidden("javac.exe", "@data\\javac.args", root, waitForExit: true);
            if (result.ExitCode != 0)
            {
                throw new InvalidOperationException("Помилка компіляції Java:\n" + result.Output);
            }
        }
        finally
        {
            // Тимчасовий arg-файл потрібний тільки під час компіляції.
            TryDelete(argsFile);
        }
    }

    private static void StartSwingApplication(string root)
    {
        // javaw.exe запускає Swing-застосунок без консольного вікна.
        string classPath = "src;lib\\" + DriverJar;

        // waitForExit: false означає, що лаунчер не чекає закриття програми.
        // Це дозволяє exe швидко завершитися, залишивши відкритим тільки Java GUI.
        ProcessResult result = RunHidden("javaw.exe", $"-cp \"{classPath}\" zlagoda.Main", root, waitForExit: false);
        if (result.ProcessStarted == false)
        {
            throw new InvalidOperationException("Не вдалося запустити javaw.exe. Перевір, чи встановлена Java.");
        }
    }

    private static ProcessResult RunHidden(string fileName, string arguments, string workingDirectory, bool waitForExit)
    {
        // Спільний метод запуску зовнішніх процесів:
        // javac.exe використовується для компіляції, javaw.exe - для запуску графічної програми.
        ProcessStartInfo startInfo = new()
        {
            FileName = fileName,
            Arguments = arguments,
            WorkingDirectory = workingDirectory,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = waitForExit,
            RedirectStandardError = waitForExit
        };

        if (waitForExit)
        {
            // Кодування задається тільки тоді, коли потоки реально перенаправляються.
            // Для javaw.exe перенаправлення не потрібне, бо він стартує окреме GUI-вікно.
            startInfo.StandardOutputEncoding = Encoding.UTF8;
            startInfo.StandardErrorEncoding = Encoding.UTF8;
        }

        using Process? process = Process.Start(startInfo);
        if (process == null)
        {
            return new ProcessResult(false, -1, "");
        }

        if (!waitForExit)
        {
            // Для javaw.exe достатньо знати, що процес стартував.
            // Чекати завершення не треба, інакше лаунчер висів би весь час роботи програми.
            return new ProcessResult(true, 0, "");
        }

        // Для javac.exe читається stdout і stderr, щоб у разі помилки показати зрозумілий текст.
        string output = process.StandardOutput.ReadToEnd() + process.StandardError.ReadToEnd();
        process.WaitForExit();
        return new ProcessResult(true, process.ExitCode, output.Trim());
    }

    private static void TryDelete(string path)
    {
        try
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
        catch
        {
            // Якщо Windows тимчасово тримає файл, це не заважає запуску програми.
        }
    }

    private static void ShowError(string message)
    {
        // MessageBoxW - стандартне Windows-вікно повідомлення.
        // Воно використовується замість Console.WriteLine, бо лаунчер зібраний як WinExe без консолі.
        MessageBoxW(IntPtr.Zero, message, "Помилка запуску ZLAGODA AIS", 0x00000010);
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int MessageBoxW(IntPtr hWnd, string text, string caption, uint type);

    private readonly record struct ProcessResult(bool ProcessStarted, int ExitCode, string Output);
}
