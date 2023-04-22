package com.ngb.wyn.common.utils;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.text.TextUtils;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.signature.EmptySignature;
import com.bumptech.glide.util.Util;
import com.ngb.wyn.common.BaseApplication;
import com.ngb.wyn.common.CommonConstants;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.List;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class FileUtil {

    private static final String TAG = "FileUtil";
    private static final int BUFFER_SIZE = 4096;
    private static final int ZIP_BUFFER_SIZE = BUFFER_SIZE * 100;

    public static String getFileMimeType(String filePath) {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        String mime = "unKnow";
        if (!TextUtils.isEmpty(filePath)) {
            mediaMetadataRetriever.setDataSource(filePath);
            mime = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
        }
        return mime;
    }

    public static File createFile(String filePath, boolean isCover) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                if (file.createNewFile()) {
                    return file;
                } else {
                    return null;
                }
            } else {
                if (isCover) {
                    if (file.delete() && file.createNewFile()) {
                        return file;
                    }
                } else {
                    return createFile(renameAndAddSuffix(filePath), false);
                }
            }
        } catch (Exception e) {
            LogUtil.e(TAG, e.getMessage());
        }
        return null;
    }

    /**
     * 创建文件夹
     *
     * @param filePath
     * @return
     */
    public static boolean createFileDir(File filePath) {
        if (filePath == null) {
            return true;
        }
        if (filePath.exists()) {
            return true;
        }
        File parentFile = filePath.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            //父文件夹不存在，则先创建父文件夹，再创建自身文件夹
            return createFileDir(parentFile) && createFileDir(filePath);
        } else {
            boolean mkDirs = filePath.mkdirs();
            boolean isSuccess = mkDirs || filePath.exists();
            if (!isSuccess) {
                LogUtil.e(TAG, "create file fail: " + filePath);
            }
            return isSuccess;
        }
    }

    public static String renameAndAddSuffix(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "" + System.currentTimeMillis();
        } else {
            int pointPos = fileName.lastIndexOf(".");
            if (pointPos == -1 && pointPos != fileName.length() - 1) {
                return fileName + " (1)";
            }
            return fileName.substring(0, pointPos) + " (1)" + fileName.substring(pointPos);
        }
    }

    public static byte[] fileToByte(String filePath) {
        byte[] buffer = null;
        FileInputStream fileInputStream = null;
        try {
            File file = new File(filePath);
            fileInputStream = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int n;
            while ((n = fileInputStream.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            bos.close();
            buffer = bos.toByteArray();
        } catch (Exception e) {
            LogUtil.e(TAG, "FileToByte, " + e.getMessage());
        } finally {
            closeSilently(fileInputStream);
        }
        return buffer;
    }

    public static File byteToFile(byte[] bytes, String filePath, boolean isCover) {
        BufferedOutputStream bufferedOutputStream = null;
        FileOutputStream fileOutputStream = null;
        File file = null;
        try {
            file = createFile(filePath, isCover);
            fileOutputStream = new FileOutputStream(file);
            bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            bufferedOutputStream.write(bytes);
        } catch (Exception e) {
            LogUtil.e(TAG, "byteToFile, error: " + e.getMessage());
        } finally {
            closeSilently(bufferedOutputStream);
            closeSilently(bufferedOutputStream);
        }
        return file;
    }

    public static boolean saveByteFile(String filePath, byte[] bytes, boolean isCover) {
        FileOutputStream fileOutputStream = null;
        try {
            File file = createFile(filePath, isCover);
            fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(bytes);
            fileOutputStream.flush();
            return true;
        } catch (Exception e) {
            LogUtil.e(e.getMessage());
        } finally {
            closeSilently(fileOutputStream);
        }
        return false;
    }

    public static boolean saveFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(fileToByte(file.getAbsolutePath()));
            return true;
        } catch (Exception e) {
            LogUtil.e(TAG, "saveFile, error: " + e.getMessage());
        } finally {
            closeSilently(fileOutputStream);
        }
        return false;
    }

    public static void closeSilently(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            LogUtil.e(e.getMessage());
        }
    }

    /**
     * @return "/storage/emulated/0"
     */
    public static String getExternalStorageDir() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    /**
     * '
     *
     * @param context 上下文
     * @param type    目录类型
     * @return "/storage/emulated/0/Android/data/com.example.demo/files" + "/type"
     * type为null，只返回前面的，如果type不为null，则返回type对应的文件目录
     */
    public static String getExternalFileDir(Context context, String type) {
        return context.getExternalFilesDir(type).getAbsolutePath();
    }

    /**
     * @param context 上下文
     * @return "/data/user/0/com.example.demo/files"
     */
    public static String getFileDir(Context context) {
        return context.getFilesDir().getAbsolutePath();
    }

    /**
     * @param context 上下文
     * @return "/data/user/0/com.example.demo/cache"
     */
    public static String getCacheFileDir(Context context) {
        return context.getCacheDir().getAbsolutePath();
    }

    /**
     * @return "/system"
     */
    public static String getRootDir() {
        return Environment.getRootDirectory().getAbsolutePath();
    }

    /**
     * 通过URL获取glide保存在本地的文件地址
     * @param url 图片地址
     * @return 返回Glide缓存该url图片在缓存中的路径
     */
    public static String getGlideFilePath(String url) {
        return BaseApplication.getInstance().getCacheDir() + File.separator
                + CommonConstants.GLIDE_CACHE_DIR_PATH + File.separator
                + getGlideSafeKey(url);
    }

    /**
     * 获取Glide在缓存中的key（4.0+版本）
     * @param url 图片url
     * @return 返回图片在缓存中的key值
     */
    public static String getGlideSafeKey(String url) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            EmptySignature signature = EmptySignature.obtain();
            signature.updateDiskCacheKey(messageDigest);
            new GlideUrl(url).updateDiskCacheKey(messageDigest);
            String safeKey = Util.sha256BytesToHex(messageDigest.digest());
            return safeKey + ".0";
        } catch (NoSuchAlgorithmException e) {
            LogUtil.e(TAG, "getGlideSafeKey, error:" + e.getMessage());
        }
        return null;
    }

    /**
     * 将zip文件解压到指定目录
     *
     * @param zip
     * @param desDir
     * @return
     */
    public static boolean unZipFile(File zip, File desDir) {
        InputStream is = null;
        OutputStream os = null;
        try (ZipFile zipFile = new ZipFile(zip)) {
            if (!desDir.exists()) {
                mkdirs(desDir);
            }
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            ZipEntry zipEntry = null;
            byte[] buffer = new byte[ZIP_BUFFER_SIZE];
            while (zipEntries.hasMoreElements()) {
                zipEntry = zipEntries.nextElement();
                String name = zipEntry.getName();
                if (name.contains("../")) {
                    return false;
                }
                File file = new File(desDir + "/" + name);
                if (zipEntry.isDirectory()) {
                    mkdirs(file);
                    continue;
                } else {
                    createNewFile(file);
                }
                os = new FileOutputStream(file);
                is = zipFile.getInputStream(zipEntry);
                while (true) {
                    int count = is.read(buffer);
                    if (count == 0) {
                        count = is.read(buffer);
                    }
                    if (count < 0) {
                        break;
                    } else if (count == 0) {
                        is.close();
                        is = null;

                        os.flush();
                        ;
                        os.close();
                        os = null;

                        throw new IOException("can't read when unzip " + zip.getName());
                    }
                    os.write(buffer, 0, count);
                }
                os.flush();
                is.close();
                is = null;
                os.close();
                os = null;
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "unZipFile, error:" + e.getMessage());
        } finally {
            closeSilently(is);
            closeSilently(os);
        }
        return true;
    }

    /**
     * @param resFilePath      要压缩的文件（夹）列表
     * @param targetFilePath   生成的压缩文件
     * @param keepDirStructure 是否保留文件结构
     * @return 是否压缩成功
     */
    public static boolean zipFiles(String resFilePath, String targetFilePath, boolean keepDirStructure) {
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(targetFilePath);
            return toZip(resFilePath, fileOutputStream, keepDirStructure);
        } catch (Exception e) {
            LogUtil.e(TAG, "zipFiles, error:" + e.getMessage());
            return false;
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    LogUtil.e(TAG, "zipFiles, close error:" + e.getMessage());
                }
            }
        }
    }

    /**
     * @param srcDir           压缩文件夹的路径
     * @param out              压缩文件输出流
     * @param keepDirStructure 是否保留原来的目录结构，true：保留目录结构；false：所有文件跑到压缩包根目录下（注意：不保留目录结构可能会出现同名文件，会压缩失败）
     * @return 返回压缩结果，成功or失败
     */
    public static boolean toZip(String srcDir, OutputStream out, boolean keepDirStructure) {
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(out);
            File sourceFile = new File(srcDir);
            return compress(sourceFile, zos, sourceFile.getName(), keepDirStructure);
        } catch (Exception e) {
            LogUtil.e(TAG, "toZip, error:" + e.getMessage());
            return false;
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException e) {
                    LogUtil.e(TAG, "toZip, close error:" + e.getMessage());
                }
            }
        }
    }

    /**
     * 批量压缩成ZIP
     *
     * @param srcFiles 需要压缩的文件列表
     * @param out      压缩文件输出流
     * @return 返回压缩结果，成功or失败
     */
    public static boolean toZip(List<File> srcFiles, OutputStream out) {
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(out);
            boolean isSuccess = true;
            for (File srcFile : srcFiles) {
                isSuccess = isSuccess && zipEntry(zos, srcFile);
            }
            return isSuccess;
        } catch (Exception e) {
            LogUtil.e(TAG, "toZip, error:" + e.getMessage());
            return false;
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException e) {
                    LogUtil.e(TAG, "toZip, close error:" + e.getMessage());
                }
            }
        }
    }

    private static boolean zipEntry(ZipOutputStream zos, File srcFile) throws Exception {
        byte[] bytes = new byte[ZIP_BUFFER_SIZE];
        zos.putNextEntry(new ZipEntry((srcFile.getName())));
        int len;
        try (FileInputStream in = new FileInputStream(srcFile)) {
            while ((len = in.read(bytes)) != -1) {
                zos.write(bytes, 0, len);
            }
            zos.closeEntry();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean compress(File sourceFile, ZipOutputStream zos, String name, boolean keepDirStructure) {
        FileInputStream fileInputStream = null;
        try {
            byte[] bytes = new byte[ZIP_BUFFER_SIZE];
            if (sourceFile.isFile()) {
                //向zip输出流中添加一个zip实体，构造器中name为zip实体的文件的名字
                zos.putNextEntry(new ZipEntry(name));
                //copy文件到zip输出流中
                int len;
                fileInputStream = new FileInputStream(sourceFile);
                while ((len = fileInputStream.read(bytes)) != -1) {
                    zos.write(bytes, 0, len);
                }
                //完成了entry
                zos.closeEntry();
                return true;
            } else {
                boolean isSuccess = true;
                File[] listFiles = sourceFile.listFiles();
                if (listFiles == null || listFiles.length == 0) {
                    //需要保留原来的文件结构时，需要对空文件夹进行处理
                    if (keepDirStructure) {
                        //空文件夹的处理
                        zos.putNextEntry(new ZipEntry(name + "/"));
                        //没有文件，不需要的copy
                        zos.closeEntry();
                    }
                } else {
                    for (File file : listFiles) {
                        //判断是否需要保留原来的文件结构
                        if (keepDirStructure) {
                            //注意：file.getName()前面需要带上父文件夹的名字加一斜杠，
                            //不然最后压缩包中就不能保留原来的文件结构，即：所有文件都跑到压缩包根目录下了
                            isSuccess = isSuccess && compress(file, zos, name + "/" + file.getName(), true);
                        } else {
                            isSuccess = isSuccess && compress(file, zos, file.getName(), false);
                        }
                    }
                }
                return isSuccess;
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "compress, error:" + e.getMessage());
            return false;
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    LogUtil.e(TAG, "compress, close error:" + e.getMessage());
                }
            }
        }
    }

    private static boolean mkdirs(File dirs) {
        boolean result = true;
        try {
            File parentFile = dirs;
            Stack<File> needCreateParentFileList = new Stack<>();
            while (true) {
                if (parentFile == null || parentFile.exists()) {
                    break;
                }
                needCreateParentFileList.push(parentFile);
                parentFile = parentFile.getParentFile();
            }
            while (!needCreateParentFileList.isEmpty()) {
                File file = needCreateParentFileList.pop();
                result = (result && mkdir(file));
            }
        } catch (Exception e) {
            result = false;
        }
        return result;
    }

    private static boolean mkdir(File dir) {
        boolean result = dir.mkdir();
        return result;
    }

    public static boolean createNewFile(File file) throws IOException {
        boolean result = true;
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            mkdirs(parentFile);
        }
        result = file.createNewFile();
        return result;
    }

}
