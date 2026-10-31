// Single-file start program for the portable Windows version of Lora (technical name ADEU LLM: folders and exe stay).
//
// The app (Java runtime included) is embedded as app.zip. On the first start of a version it is unpacked
// to %LOCALAPPDATA%\ADEU LLM\app\<version> – no admin rights, no installation – and then started.
// Later starts launch it right away. Older versions are removed. Built with the C# compiler of the
// .NET Framework that is part of every Windows 10/11 (see .github/scripts/windows-portable.ps1).
using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Windows.Forms;

[assembly: AssemblyTitle("Lora")]
[assembly: AssemblyProduct("Lora")]
[assembly: AssemblyDescription("Lora – Der freundliche Helfer für Forschung, Lehre und Verwaltung an der TU Dresden")]
[assembly: AssemblyVersion("@VERSION@")]
[assembly: AssemblyFileVersion("@VERSION@")]

static class Launcher
{
    const string Version = "@VERSION@";
    const string AppFolder = "ADEU LLM";
    const string AppExe = "ADEU LLM.exe";

    [STAThread]
    static int Main(string[] args)
    {
        string root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ADEU LLM", "app");
        string dir = Path.Combine(root, Version);
        string exe = Path.Combine(Path.Combine(dir, AppFolder), AppExe);
        string marker = Path.Combine(dir, ".complete");
        try
        {
            if (!File.Exists(marker) || !File.Exists(exe))
            {
                Exception error = Unpack(root, dir, marker);
                if (error != null)
                {
                    MessageBox.Show(
                        "Lora konnte nicht vorbereitet werden:\n\n" + error.Message +
                        "\n\nAlternative: die ZIP-Version herunterladen, entpacken und \"ADEU LLM.exe\" starten.",
                        "Lora", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    return 1;
                }
            }
            RemoveOldVersions(root, dir);
            var start = new ProcessStartInfo(exe, JoinArgs(args));
            start.WorkingDirectory = Path.GetDirectoryName(exe);
            start.UseShellExecute = false;
            Process.Start(start);
            return 0;
        }
        catch (Exception e)
        {
            MessageBox.Show("Lora konnte nicht gestartet werden:\n\n" + e.Message, "Lora",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    /** Unpacks app.zip with a small progress window; returns the error or null. */
    static Exception Unpack(string root, string dir, string marker)
    {
        Exception error = null;
        Application.EnableVisualStyles();
        var form = new Form();
        form.Text = "Lora";
        form.FormBorderStyle = FormBorderStyle.FixedDialog;
        form.StartPosition = FormStartPosition.CenterScreen;
        form.ClientSize = new Size(380, 96);
        form.MaximizeBox = false;
        form.MinimizeBox = false;
        form.ControlBox = false;
        form.ShowInTaskbar = true;
        try { form.Icon = Icon.ExtractAssociatedIcon(Assembly.GetExecutingAssembly().Location); } catch (Exception) { }
        var label = new Label();
        label.Text = "Lora " + Version + " wird beim ersten Start vorbereitet …";
        label.SetBounds(16, 18, 348, 22);
        var bar = new ProgressBar();
        bar.SetBounds(16, 50, 348, 22);
        bar.Minimum = 0;
        bar.Maximum = 1000;
        form.Controls.Add(label);
        form.Controls.Add(bar);

        var worker = new Thread(() =>
        {
            try
            {
                Extract(root, dir, marker, delegate(int permille)
                {
                    try { form.BeginInvoke((MethodInvoker)delegate { bar.Value = Math.Min(1000, permille); }); } catch (Exception) { }
                });
            }
            catch (Exception e)
            {
                error = e;
            }
            try { form.BeginInvoke((MethodInvoker)delegate { form.Close(); }); } catch (Exception) { }
        });
        worker.IsBackground = true;
        form.Shown += delegate { worker.Start(); };
        Application.Run(form);
        worker.Join();
        return error;
    }

    static void Extract(string root, string dir, string marker, Action<int> progress)
    {
        Directory.CreateDirectory(root);
        string temp = dir + ".tmp-" + Process.GetCurrentProcess().Id;
        if (Directory.Exists(temp)) Directory.Delete(temp, true);
        Directory.CreateDirectory(temp);
        using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("app.zip"))
        {
            if (stream == null) throw new InvalidOperationException("Die App fehlt in dieser Datei (app.zip).");
            using (var zip = new ZipArchive(stream, ZipArchiveMode.Read))
            {
                long total = 0, done = 0;
                foreach (ZipArchiveEntry e in zip.Entries) total += e.Length;
                string baseDir = Path.GetFullPath(temp) + Path.DirectorySeparatorChar;
                foreach (ZipArchiveEntry e in zip.Entries)
                {
                    string target = Path.GetFullPath(Path.Combine(temp, e.FullName));
                    if (!target.StartsWith(baseDir, StringComparison.OrdinalIgnoreCase)) continue;
                    if (e.FullName.EndsWith("/") || e.FullName.EndsWith("\\"))
                    {
                        Directory.CreateDirectory(target);
                        continue;
                    }
                    Directory.CreateDirectory(Path.GetDirectoryName(target));
                    e.ExtractToFile(target, true);
                    done += e.Length;
                    if (total > 0) progress((int)(done * 1000 / total));
                }
            }
        }
        File.WriteAllText(Path.Combine(temp, ".complete"), Version, Encoding.UTF8);
        if (Directory.Exists(dir))
        {
            // Left over from an interrupted start (no marker) – replace it.
            if (File.Exists(marker)) { Directory.Delete(temp, true); return; }
            Directory.Delete(dir, true);
        }
        try
        {
            Directory.Move(temp, dir);
        }
        catch (IOException)
        {
            // Another start was faster.
            if (!File.Exists(marker)) throw;
            Directory.Delete(temp, true);
        }
    }

    /** Deletes other versions; ones that are still running stay until the next start. */
    static void RemoveOldVersions(string root, string current)
    {
        try
        {
            foreach (string d in Directory.GetDirectories(root))
            {
                if (string.Equals(Path.GetFullPath(d), Path.GetFullPath(current), StringComparison.OrdinalIgnoreCase)) continue;
                try { Directory.Delete(d, true); } catch (Exception) { }
            }
        }
        catch (Exception) { }
    }

    static string JoinArgs(string[] args)
    {
        var sb = new StringBuilder();
        foreach (string a in args)
        {
            if (sb.Length > 0) sb.Append(' ');
            sb.Append('"').Append(a.Replace("\"", "\\\"")).Append('"');
        }
        return sb.ToString();
    }
}
